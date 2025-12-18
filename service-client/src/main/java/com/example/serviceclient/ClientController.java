package com.example.serviceclient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

@RestController
@RequestMapping("/api/clients")
public class ClientController {

    // 1. RestTemplate
    @Autowired
    private RestTemplate restTemplate;

    // 2. Feign
    @Autowired
    private CarFeignClient carFeignClient;

    // 3. WebClient
    @Autowired
    private WebClient.Builder webClientBuilder;

    private final String SERVICE_URL = "http://service-voiture/api/cars/byClient/";

    @GetMapping("/{id}/car/rest")
    public CarResponse getCarRest(@PathVariable Long id) {
        return restTemplate.getForObject(SERVICE_URL + id, CarResponse.class);
    }

    @GetMapping("/{id}/car/feign")
    public CarResponse getCarFeign(@PathVariable Long id) {
        return carFeignClient.getCar(id);
    }

    @GetMapping("/{id}/car/webclient")
    public CarResponse getCarWebClient(@PathVariable Long id) {
        return webClientBuilder.build()
                .get()
                .uri(SERVICE_URL + id)
                .retrieve()
                .bodyToMono(CarResponse.class)
                .block(); // BLOCK = Synchrone pour ce TP
    }
}