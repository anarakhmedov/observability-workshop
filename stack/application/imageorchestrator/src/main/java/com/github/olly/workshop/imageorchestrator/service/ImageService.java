package com.github.olly.workshop.imageorchestrator.service;

import com.github.olly.workshop.imageorchestrator.config.LogTraceContextUtil;
import com.github.olly.workshop.imageorchestrator.model.Image;
import com.github.olly.workshop.imageorchestrator.model.TransformationRequest;
import com.github.olly.workshop.imageorchestrator.service.clients.ImageHolderClient;
import com.github.olly.workshop.imageorchestrator.service.clients.ImageHolderUploadClient;
import com.github.olly.workshop.springevents.service.EventService;
import org.apache.commons.lang.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class ImageService {

    @Autowired
    TransformationService transformationService;

    @Autowired
    private ImageOrchestratorMetricsService imageOrchestratorMetricsService;

    @Autowired
    private EventService eventService;

    @Autowired
    private ImageHolderClient imageHolderClient;

    @Autowired
    private ImageHolderUploadClient imageHolderUploadClient;

    @Autowired
    private LogTraceContextUtil contextUtil;

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageService.class);

    public Image transform(TransformationRequest transformationRequest) {

        // load the image from imageholder
        Image originalImage;
        try {
            this.eventService.addFieldToActiveEvent("content.transformed.imageId", transformationRequest.getImageId());
            originalImage = loadImage(transformationRequest.getImageId());
        } catch (Throwable e) {
            this.eventService.addFieldToActiveEvent("app.error", 1);
            this.eventService.addFieldToActiveEvent("app.exception", e);
            LOGGER.error("Failed loading image with id " + transformationRequest.getImageId() + " from imageholder", e);
            return null;
        }
        contextUtil.put(originalImage);
        this.eventService.addFieldToActiveEvent("content.size", originalImage.getSize());

        final Image transformedImage = transformationService.transform(originalImage, transformationRequest.getTransformations());

        this.eventService.addFieldToActiveEvent("content.transformed.type", transformedImage.getMimeType());
        imageOrchestratorMetricsService.imageTransformed(originalImage, transformedImage, transformationRequest);

        if (BooleanUtils.isTrue(transformationRequest.getPersist())) {
            this.eventService.addFieldToActiveEvent("content.transformed.persist", true);
            imageHolderUploadClient.upload(transformedImage, transformationRequest.getName());
        }

        return transformedImage;
    }

    private Image loadImage(String id) {
        ResponseEntity<byte[]> response = imageHolderClient.getImage(id);
        return new Image(response.getBody(), response.getHeaders().getContentType().toString(), id);
    }
}
