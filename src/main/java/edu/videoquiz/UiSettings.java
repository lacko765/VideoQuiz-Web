package edu.videoquiz;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/** Only public UI limits; never expose environment configuration or credentials. */
@RestController
public class UiSettings {
    @Value("${videoquiz.youtube-api-timeout-ms:60000}") private int api;
    @Value("${videoquiz.youtube-player-timeout-ms:60000}") private int player;
    @Value("${videoquiz.youtube-metadata-timeout-ms:60000}") private int metadata;
    private int bounded(int n){return Math.max(10000,Math.min(120000,n));}
    @GetMapping("/api/settings") public Map<String,Integer> settings(){return Map.of("youtubeApiMs",bounded(api),"youtubePlayerMs",bounded(player),"youtubeMetadataMs",bounded(metadata));}
}
