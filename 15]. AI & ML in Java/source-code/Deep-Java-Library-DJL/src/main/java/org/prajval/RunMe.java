package org.prajval;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.djl.ModelException;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.translate.TranslateException;

public class RunMe {
	
	private static final Logger logger =
            LoggerFactory.getLogger(ObjectDetectionWithTensorflowSavedModel.class);
	
	public static void main(String[] args) throws IOException, ModelNotFoundException, TranslateException, ModelException 
    {
		//For Object Detection
        DetectedObjects detection = ObjectDetectionWithTensorflowSavedModel.predict();
        logger.info("{}", detection);
        
        //For TextDetection
    }

}
