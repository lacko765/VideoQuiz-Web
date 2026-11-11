package edu.videoquiz;

import edu.videoquiz.core.*;
import java.nio.file.Path;
import java.sql.*;
import java.util.Properties;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.*;

@Configuration @Profile("cloud") @EnableScheduling
public class CloudConfiguration {
    private CloudStore store;
    private CloudStore.ConnectionFactory connections;
    @Bean(destroyMethod="close") Store cloudStore(Environment env) throws Exception {
        if(env.getProperty("videoquiz.gemini-key","").isBlank())throw new IllegalStateException("Set GEMINI_API_KEY in the hosting secret variables.");
        String host=env.getRequiredProperty("VIDEOQUIZ_DB_HOST");
        if(!host.matches("[a-z0-9.-]+\\.pooler\\.supabase\\.com"))throw new IllegalArgumentException("Use the Supabase session pooler hostname on port 5432.");
        Properties props=new Properties();
        props.setProperty("user",env.getRequiredProperty("VIDEOQUIZ_DB_USER"));
        props.setProperty("password",env.getRequiredProperty("VIDEOQUIZ_DB_PASSWORD"));
        props.setProperty("sslmode","verify-full");props.setProperty("sslfactory","org.postgresql.ssl.DefaultJavaSSLFactory");
        props.setProperty("connectTimeout","15");props.setProperty("socketTimeout","30");props.setProperty("tcpKeepAlive","true");
        connections=()->DriverManager.getConnection("jdbc:postgresql://"+host+":5432/postgres",props);
        Connection db;
       
try {
    db = connections.open();
} catch (SQLException e) {
    Throwable root = e;
    while (root.getCause() != null) {
        root = root.getCause();
    }

    System.err.println("DB_DIAG SQLState=" + e.getSQLState()
        + " root=" + root.getClass().getSimpleName());

    throw new IllegalStateException("Cannot connect to cloud database.");
}

        try {
            var objects=new SupabaseObjects(env.getRequiredProperty("SUPABASE_URL"),env.getRequiredProperty("SUPABASE_SERVICE_ROLE_KEY"),env.getProperty("VIDEOQUIZ_BUCKET","videoquiz-private"));
            objects.verifyPrivateBucket();
            store=new CloudStore(Path.of(env.getRequiredProperty("videoquiz.data-dir")),db,objects);
            store.recover();return store;
        }catch(Exception e){db.close();throw e;}
    }
    @Scheduled(fixedDelay=60000) void recoverInterruptedJobs() {if(store!=null)try{store.reconnectIfNeeded(connections);store.recover();}catch(Exception e){System.err.println("Cloud recovery is waiting for database access.");}}
}
