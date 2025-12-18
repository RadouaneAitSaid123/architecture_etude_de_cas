package com.example.servicevoiture;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/cars")
public class CarController {

    @GetMapping("/byClient/{clientId}")
    public Map<String, Object> getCarByClient(@PathVariable Long clientId) {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return Map.of(
                "id", 10,
                "marque", "Toyota",
                "modele", "Yaris",
                "clientId", clientId
        );
    }
}