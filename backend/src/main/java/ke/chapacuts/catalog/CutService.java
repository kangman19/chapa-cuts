package ke.chapacuts.catalog;

/** A cut on the menu. {@code price} is null for "Something else", which is priced at the shop. */
public record CutService(String id, String name, Integer price) {

    public boolean isPricedAtShop() {
        return price == null;
    }
}
