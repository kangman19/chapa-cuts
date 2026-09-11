package ke.chapacuts.upload;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import ke.chapacuts.api.CustomerFacingException;
import org.springframework.stereotype.Component;

/** Photos live in memory only. Keeps the newest 50 and drops the oldest when full. */
@Component
public class UploadStore {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int MAX_RETAINED = 50;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    public record StoredImage(String id, String contentType, byte[] bytes) {
    }

    private final LinkedHashMap<String, StoredImage> images = new LinkedHashMap<>();

    public synchronized StoredImage store(String contentType, byte[] bytes) {
        String type = contentType == null ? "" : contentType.toLowerCase().split(";")[0].trim();
        if (!ALLOWED_TYPES.contains(type)) {
            throw new CustomerFacingException("Please upload a JPEG, PNG or WebP photo.");
        }
        if (bytes.length == 0) {
            throw new CustomerFacingException("That file looks empty. Try another photo.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new CustomerFacingException("That photo is over 5 MB. Try a smaller one.");
        }
        String id = UUID.randomUUID().toString().replace("-", "");
        StoredImage image = new StoredImage(id, type, bytes);
        images.put(id, image);
        while (images.size() > MAX_RETAINED) {
            Iterator<Map.Entry<String, StoredImage>> oldest = images.entrySet().iterator();
            oldest.next();
            oldest.remove();
        }
        return image;
    }

    public synchronized Optional<StoredImage> find(String id) {
        return Optional.ofNullable(images.get(id));
    }

    public synchronized boolean exists(String id) {
        return id != null && images.containsKey(id);
    }
}
