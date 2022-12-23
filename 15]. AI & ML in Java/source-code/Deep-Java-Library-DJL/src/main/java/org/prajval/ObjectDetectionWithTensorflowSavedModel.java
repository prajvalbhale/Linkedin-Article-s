package org.prajval;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.annotations.SerializedName;

import ai.djl.Application;
import ai.djl.ModelException;
import ai.djl.inference.Predictor;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.output.BoundingBox;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.modality.cv.output.Rectangle;
import ai.djl.modality.cv.util.NDImageUtils;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.types.DataType;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.NoBatchifyTranslator;
import ai.djl.translate.TranslateException;
import ai.djl.translate.TranslatorContext;
import ai.djl.util.JsonUtils;

public final class ObjectDetectionWithTensorflowSavedModel {

    private static final Logger logger =
            LoggerFactory.getLogger(ObjectDetectionWithTensorflowSavedModel.class);

    private ObjectDetectionWithTensorflowSavedModel() {}

    public static DetectedObjects predict() throws IOException, ModelException, TranslateException 
    {
        Path imageFile = Paths.get("src/test/input_img/dog_bike_car.jpg");        
        //for image detection
        Image img = ImageFactory.getInstance().fromFile(imageFile);

        String modelUrl =
                "http://download.tensorflow.org/models/object_detection/tf2/20200711/ssd_mobilenet_v2_320x320_coco17_tpu-8.tar.gz";

        Criteria<Image, DetectedObjects> criteria =
                Criteria.builder()
                        .optApplication(Application.CV.OBJECT_DETECTION)
                        .setTypes(Image.class, DetectedObjects.class)
                        .optModelUrls(modelUrl)
                        .optModelName("saved_model")
                        .optTranslator(new MyTranslator())
                        .optEngine("TensorFlow")
                        .optProgress(new ProgressBar())
                        .build();
        
      try (ZooModel<Image, DetectedObjects> model = criteria.loadModel();
           Predictor<Image, DetectedObjects> predictor = model.newPredictor()) 
        {
            DetectedObjects detection = predictor.predict(img);            
            saveBoundingBoxImage(img, detection);
            return detection;
        }        
    }

    private static void saveBoundingBoxImage(Image img, DetectedObjects detection) throws IOException 
    {
        Path outputDir = Paths.get("build/output");
        Files.createDirectories(outputDir);

        img.drawBoundingBoxes(detection);

        Path imagePath = outputDir.resolve("Detected-Objects.png");
        // OpenJDK can't save jpg with alpha channel
        img.save(Files.newOutputStream(imagePath), "png");
        logger.info("Detected objects image has been saved in: {}", imagePath);
    }
    

    static Map<Integer, String> loadSynset() throws IOException 
    {
        URL synsetUrl =
                new URL(
                        "https://raw.githubusercontent.com/tensorflow/models/master/research/object_detection/data/mscoco_label_map.pbtxt");
        Map<Integer, String> map = new ConcurrentHashMap<>();
        int maxId = 0;
        try (InputStream is = new BufferedInputStream(synsetUrl.openStream());
                Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name())) 
        {
            scanner.useDelimiter("item ");
            while (scanner.hasNext()) 
            {
                String content = scanner.next();
                content = content.replaceAll("(\"|\\d)\\n\\s", "$1,");
                Item item = JsonUtils.GSON.fromJson(content, Item.class);
                map.put(item.id, item.displayName);
                if (item.id > maxId) 
                {
                    maxId = item.id;
                }
            }
        }
        return map;
    }

    private static final class Item 
    {
        int id;

        @SerializedName("display_name")
        String displayName;
    }

    private static final class MyTranslator implements NoBatchifyTranslator<Image, DetectedObjects> 
    {
        private Map<Integer, String> classes;
        private int maxBoxes;
        private float threshold;

        MyTranslator() 
        {
            maxBoxes = 10;
            threshold = 0.7f;
        }

        /** {@inheritDoc} */
        @Override
        public NDList processInput(TranslatorContext ctx, Image input) 
        {
            // input to tf object-detection models is a list of tensors, hence NDList
            NDArray array = input.toNDArray(ctx.getNDManager(), Image.Flag.COLOR);
            // optionally resize the image for faster processing
            array = NDImageUtils.resize(array, 224);
            // tf object-detection models expect 8 bit unsigned integer tensor
            array = array.toType(DataType.UINT8, true);
            array = array.expandDims(0); // tf object-detection models expect a 4 dimensional input
            return new NDList(array);
        }

        /** {@inheritDoc} */
        @Override
        public void prepare(TranslatorContext ctx) throws IOException {
            if (classes == null) {
                classes = loadSynset();
            }
        }

        /** {@inheritDoc} */
        @Override
        public DetectedObjects processOutput(TranslatorContext ctx, NDList list) {
            // output of tf object-detection models is a list of tensors, hence NDList in djl
            // output NDArray order in the list are not guaranteed

            int[] classIds = null;
            float[] probabilities = null;
            NDArray boundingBoxes = null;
            for (NDArray array : list) {
                if ("detection_boxes".equals(array.getName())) {
                    boundingBoxes = array.get(0);
                } else if ("detection_scores".equals(array.getName())) {
                    probabilities = array.get(0).toFloatArray();
                } else if ("detection_classes".equals(array.getName())) {
                    // class id is between 1 - number of classes
                    classIds = array.get(0).toType(DataType.INT32, true).toIntArray();
                }
            }
            Objects.requireNonNull(classIds);
            Objects.requireNonNull(probabilities);
            Objects.requireNonNull(boundingBoxes);

            List<String> retNames = new ArrayList<>();
            List<Double> retProbs = new ArrayList<>();
            List<BoundingBox> retBB = new ArrayList<>();

            // result are already sorted
            for (int i = 0; i < Math.min(classIds.length, maxBoxes); ++i) {
                int classId = classIds[i];
                double probability = probabilities[i];
                // classId starts from 1, -1 means background
                if (classId > 0 && probability > threshold) {
                    String className = classes.getOrDefault(classId, "#" + classId);
                    float[] box = boundingBoxes.get(i).toFloatArray();
                    float yMin = box[0];
                    float xMin = box[1];
                    float yMax = box[2];
                    float xMax = box[3];
                    Rectangle rect = new Rectangle(xMin, yMin, xMax - xMin, yMax - yMin);
                    retNames.add(className);
                    retProbs.add(probability);
                    retBB.add(rect);
                }
            }

            return new DetectedObjects(retNames, retProbs, retBB);
        }
    }
}