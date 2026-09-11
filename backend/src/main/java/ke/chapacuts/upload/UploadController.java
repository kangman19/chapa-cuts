package ke.chapacuts.upload;

import java.io.IOException;
import java.util.Map;
import ke.chapacuts.api.CustomerFacingException;
import ke.chapacuts.api.NotFoundException;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final UploadStore uploads;

    public UploadController(UploadStore uploads) {
        this.uploads = uploads;
    }

    @PostMapping
    public Map<String, String> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new CustomerFacingException("Choose a photo first.");
        }
        UploadStore.StoredImage image = uploads.store(file.getContentType(), file.getBytes());
        return Map.of("imageId", image.id());
    }

    @GetMapping("/{imageId}")
    public ResponseEntity<byte[]> serve(@PathVariable String imageId) {
        UploadStore.StoredImage image = uploads.find(imageId)
                .orElseThrow(() -> new NotFoundException("That photo is no longer available."));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.noStore())
                .body(image.bytes());
    }
}
