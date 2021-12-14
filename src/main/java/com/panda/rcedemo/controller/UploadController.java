package com.panda.rcedemo.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@Controller
public class UploadController {
    private static final Logger LOGGER = LoggerFactory.getLogger(UploadController.class);

    @GetMapping("/upload")
    @ResponseBody
    public String upload() {
        return "upload";
    }

    @PostMapping("/upload")
    @ResponseBody
    public String upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return "Upload failed, please select a file！！";
        }

        String fileName = file.getOriginalFilename();
        String filePath = Thread.currentThread().getContextClassLoader().getResource("").getPath();;
        System.out.println(filePath);
        File dest = new File(filePath,fileName);
        try {
            file.transferTo(dest);
            LOGGER.info("Upload succeeded!!");
            return "Upload succeeded!!";
        } catch (IOException e) {
            LOGGER.error(e.toString(), e);
        }
        return "Upload failed!!";
    }
}