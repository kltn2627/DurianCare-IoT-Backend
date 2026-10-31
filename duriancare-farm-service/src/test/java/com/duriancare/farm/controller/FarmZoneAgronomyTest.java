package com.duriancare.farm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.duriancare.farm.domain.*;
import com.duriancare.farm.repository.FarmRepository;
import com.duriancare.farm.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FarmZoneAgronomyTest {
    private MockMvc mvc;
    private FarmRepository repository;
    private AtomicReference<Document> stored;
    private MappingMongoConverter converter;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() throws Exception {
        var conversions = MongoCustomConversions.create(adapter -> {});
        var mapping = new MongoMappingContext();
        mapping.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        mapping.afterPropertiesSet();
        converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, mapping);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
        stored = new AtomicReference<>(new Document());
        converter.write(new Farm("farm-1", "owner-1", "Farm", null, null, null, null, null, null,
                FarmStatus.ACTIVE, List.of(), Instant.now(), Instant.now()), stored.get());
        repository = mock(FarmRepository.class);
        when(repository.findById("farm-1")).thenAnswer(inv -> Optional.of(converter.read(Farm.class, stored.get())));
        when(repository.save(any(Farm.class))).thenAnswer(inv -> {
            var document = new Document();
            converter.write(inv.getArgument(0), document);
            stored.set(document);
            return converter.read(Farm.class, document);
        });
        var service = new FarmCatalogService(repository, org.mockito.Mockito.mock(com.duriancare.farm.repository.FarmAuthorizationRepository.class), new AgronomistRolePolicy());
        mvc = MockMvcBuilders.standaloneSetup(new FarmCatalogController(service, new RequestActorResolver()))
                .setControllerAdvice(new FarmApiExceptionHandler()).build();
    }

    private String create(String agronomy) throws Exception {
        var result = mvc.perform(post("/api/v1/farms/farm-1/zones")
                .header("X-Auth-User-Id", "owner-1").header("X-Auth-Role", "FARMER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Khu RI6\",\"status\":\"ACTIVE\",\"agronomy\":" + agronomy + "}"))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("id").asText();
    }

    @Test
    void richProfileSurvivesMongoMappingReadUpdateAndArchive() throws Exception {
        String id = create("""
                {"cropType":"DURIAN","variety":"RI6","plantingDate":"2020-06-10",
                 "initialTreeCount":180,"currentTreeCount":164,"growthStage":"PRODUCTIVE",
                 "seedSource":"Nursery","plantingDistance":"8m x 8m","soilType":"Basalt",
                 "waterSource":"Pond","irrigationMethod":"DRIP","drainageStatus":"Good",
                 "locationDescription":"East","imageUrls":["https://example.com/zone.jpg"],"notes":"Care notes",
                 "harvestHistories":[{"id":"h-1","seasonName":"2025 crop","harvestYear":2025,
                 "harvestedAt":"2025-08-10","yieldValue":18.4,"yieldUnit":"tấn","qualityNote":"A","notes":"Dry harvest"}]}
                """);
        mvc.perform(get("/api/v1/farms/farm-1/zones")
                .header("X-Auth-User-Id","owner-1").header("X-Auth-Role","FARMER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].agronomy.variety").value("RI6"))
                .andExpect(jsonPath("$[0].agronomy.harvestHistories[0].yieldValue").value(18.4))
                .andExpect(jsonPath("$[0].agronomy.imageUrls[0]").value("https://example.com/zone.jpg"));
        mvc.perform(patch("/api/v1/farms/farm-1/zones/" + id)
                .header("X-Auth-User-Id","owner-1").header("X-Auth-Role","FARMER")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.agronomy.currentTreeCount").value(164));
        mvc.perform(delete("/api/v1/farms/farm-1/zones/" + id)
                .header("X-Auth-User-Id","owner-1").header("X-Auth-Role","FARMER"))
                .andExpect(status().isNoContent());
        var zone = converter.read(Farm.class, stored.get()).zones().getFirst();
        assertThat(zone.status()).isEqualTo(ZoneStatus.ARCHIVED);
        assertThat(zone.agronomy().variety()).isEqualTo("RI6");
        assertThat(zone.agronomy().harvestHistories().getFirst().harvestedAt().toString()).isEqualTo("2025-08-10");
    }

    @Test
    void profileCanBeReplacedAndOptionalDataExplicitlyCleared() throws Exception {
        String id = create("{\"variety\":\"RI6\",\"currentTreeCount\":10,\"imageUrls\":[\"https://example.com/a.jpg\"]}");
        mvc.perform(patch("/api/v1/farms/farm-1/zones/" + id)
                .header("X-Auth-User-Id","owner-1").header("X-Auth-Role","FARMER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"agronomy\":{\"variety\":\"Monthong\",\"currentTreeCount\":0,\"imageUrls\":[],\"harvestHistories\":[]}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.agronomy.currentTreeCount").value(0))
                .andExpect(jsonPath("$.agronomy.imageUrls").isEmpty());
        assertThat(converter.read(Farm.class, stored.get()).zones().getFirst().agronomy().variety()).isEqualTo("Monthong");
    }

    @Test
    void oldRequestsAndDocumentsRemainReadable() throws Exception {
        String id = create("null");
        var document = stored.get().getList("zones", Document.class).getFirst();
        document.remove("agronomy");
        mvc.perform(patch("/api/v1/farms/farm-1/zones/" + id)
                .header("X-Auth-User-Id","owner-1").header("X-Auth-Role","FARMER")
                .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"Old client update\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("Old client update"));
        assertThat(converter.read(Farm.class, stored.get()).zones().getFirst().agronomy()).isNull();
    }

    @Test
    void rejectsInvalidAgronomyWithoutWriting() throws Exception {
        for (String invalid : List.of(
                "{\"initialTreeCount\":10,\"currentTreeCount\":11}",
                "{\"currentTreeCount\":-1}",
                "{\"plantingDate\":\"2999-01-01\"}",
                "{\"growthStage\":\"INVALID\"}",
                "{\"imageUrls\":[\"javascript:alert(1)\"]}",
                "{\"plantingDate\":\"2020-01-01\",\"harvestHistories\":[{\"seasonName\":\"Early\",\"harvestYear\":2019}]}",
                "{\"harvestHistories\":[{\"seasonName\":\"Crop\",\"harvestYear\":2024,\"harvestedAt\":\"2025-08-10\"}]}",
                "{\"harvestHistories\":[{\"seasonName\":\"\",\"harvestYear\":2025}]}")) {
            mvc.perform(post("/api/v1/farms/farm-1/zones")
                    .header("X-Auth-User-Id","owner-1").header("X-Auth-Role","FARMER")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Invalid\",\"agronomy\":" + invalid + "}"))
                    .andExpect(status().isBadRequest());
        }
        verify(repository, never()).save(any());
    }

    @Test
    void foreignOwnerCannotChangeProfile() throws Exception {
        mvc.perform(post("/api/v1/farms/farm-1/zones")
                .header("X-Auth-User-Id","other-owner").header("X-Auth-Role","FARMER")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Foreign\",\"agronomy\":{\"variety\":\"RI6\"}}"))
                .andExpect(status().isForbidden());
        verify(repository, never()).save(any());
    }
}

