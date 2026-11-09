package edu.videoquiz;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.DisposableBean;
import java.nio.file.*;
import java.nio.channels.*;

/** Fail closed if production secrets/storage are absent; prevent multiple writers on one disk. */
@Component @Profile("prod & !cloud")
public class ProductionStorage implements DisposableBean {
    private final FileChannel channel;
    private final FileLock lock;
    public ProductionStorage(@Value("${videoquiz.data-dir}") String directory,
                             @Value("${videoquiz.gemini-key}") String key) throws Exception {
        if(key.isBlank()) throw new IllegalStateException("Set GEMINI_API_KEY in the hosting platform's secret variables.");
        Path root=Path.of(directory).normalize();
        if(!root.isAbsolute() || !Files.isDirectory(root) || !Files.isWritable(root))
            throw new IllegalStateException("Mount a writable persistent volume and set VIDEOQUIZ_DATA_DIR to its absolute path.");
        if(System.getenv("RAILWAY_PROJECT_ID")!=null) {
            String mount=System.getenv("RAILWAY_VOLUME_MOUNT_PATH");
            if(mount==null || !root.startsWith(Path.of(mount).normalize()))
                throw new IllegalStateException("Railway persistent volume is missing or VIDEOQUIZ_DATA_DIR is outside it.");
        }
        channel=FileChannel.open(root.resolve(".instance.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        FileLock acquired;
        try { acquired=channel.tryLock(); } catch(Exception e) {channel.close();throw e;}
        if(acquired==null) {channel.close();throw new IllegalStateException("This data volume is already in use. Run exactly one replica.");}
        lock=acquired;
    }
    @Override public void destroy() throws Exception {lock.release();channel.close();}
}
