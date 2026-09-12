package ke.chapacuts.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ServiceCatalog {

    public static final int DEPOSIT = 1;
    public static final String OTHER_ID = "other";

    private static final List<CutService> SERVICES = List.of(
            new CutService("taper", "Taper fade", 500),
            new CutService("skin", "Skin fade", 700),
            new CutService("lineup", "Line-up", 300),
            new CutService("beard", "Cut and beard trim", 800),
            new CutService("kids", "Kids cut", 400),
            new CutService(OTHER_ID, "Something else", null));

    public List<CutService> all() {
        return SERVICES;
    }

    public Optional<CutService> find(String id) {
        return SERVICES.stream().filter(s -> s.id().equals(id)).findFirst();
    }
}
