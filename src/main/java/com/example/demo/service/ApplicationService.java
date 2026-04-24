package com.example.demo.service;

import com.example.demo.model.ChirpStackApp;
import com.example.demo.repository.ChirpStackAppRepository;
import io.chirpstack.api.ApplicationServiceGrpc;
import io.chirpstack.api.ListApplicationsRequest;
import io.chirpstack.api.ListApplicationsResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ApplicationService {

    @Autowired
    private ApplicationServiceGrpc.ApplicationServiceBlockingStub appStub;
    
    @Autowired
    private ChirpStackAppRepository appRepository;

    @Value("${chirpstack.tenant-id}")
    private String tenantId;

    public List<ChirpStackApp> syncFromChirpStack() {
        ListApplicationsRequest req = ListApplicationsRequest.newBuilder()
                .setTenantId(tenantId).setLimit(100).build();
        ListApplicationsResponse resp = appStub.list(req);
        
        List<ChirpStackApp> entities = resp.getResultList().stream().map(item -> {
            ChirpStackApp app = new ChirpStackApp();
            app.setId(item.getId());
            app.setName(item.getName());
            app.setDescription(item.getDescription());
            app.setLastSyncTime(LocalDateTime.now());
            return app;
        }).collect(Collectors.toList());
        return appRepository.saveAll(entities);
    }

    public List<ChirpStackApp> getAllFromLocal() {
        return appRepository.findAll();
    }
}