package com.example.demo.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.demo.model.ChirpStackApp;
import com.example.demo.repository.ChirpStackAppRepository;

import io.chirpstack.api.ApplicationServiceGrpc;
import io.chirpstack.api.ListApplicationsRequest;
import io.chirpstack.api.ListApplicationsResponse;

// ApplicationService.java
@Service
public class ApplicationService {
    @Autowired
    private ApplicationServiceGrpc.ApplicationServiceBlockingStub appStub;
    @Autowired
    private ChirpStackAppRepository appRepository;

    public List<ChirpStackApp> syncFromChirpStack() {
        ListApplicationsResponse resp = appStub.list(ListApplicationsRequest.newBuilder().setLimit(100).build());
        List<ChirpStackApp> entities = resp.getResultList().stream().map(item -> {
            ChirpStackApp app = new ChirpStackApp();
            app.setId(item.getId());
            app.setName(item.getName());
            return app;
        }).collect(Collectors.toList());
        return appRepository.saveAll(entities);
    }
}