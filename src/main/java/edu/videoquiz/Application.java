package edu.videoquiz;

import edu.videoquiz.core.*;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;

@SpringBootApplication
public class Application {
    public static void main(String[] args) { SpringApplication.run(Application.class,args); }
    @Bean @org.springframework.context.annotation.Profile("!cloud") Store store(@Value("${videoquiz.data-dir}") String dir, org.springframework.beans.factory.ObjectProvider<ProductionStorage> guard) throws Exception { guard.getIfAvailable(); Store s = new Store(Path.of(dir)); s.recover(); return s; }
    @Bean Gemini gemini(@Value("${videoquiz.gemini-key}") String key,@Value("${videoquiz.gemini-model}") String model) { return new Gemini(key,model); }
}
