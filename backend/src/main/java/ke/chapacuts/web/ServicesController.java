package ke.chapacuts.web;

import java.util.List;
import ke.chapacuts.catalog.CutService;
import ke.chapacuts.catalog.ServiceCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServicesController {

    private final ServiceCatalog catalog;

    public ServicesController(ServiceCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/api/services")
    public List<CutService> services() {
        return catalog.all();
    }
}
