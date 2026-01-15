package nfc;

import javafx.scene.image.Image;

public class ImageLoader {
    public static Image loadSafe(String fileName) {
        try {
            java.net.URL url = ImageLoader.class.getResource("/nfc/" + fileName);
            if (url != null) return new Image(url.toExternalForm());

            System.out.println("⚠️ Missing image: " + fileName);
            return new Image("https://via.placeholder.com/60x60.png?text=Missing");
        } catch (Exception e) {
            e.printStackTrace();
            return new Image("https://via.placeholder.com/60x60.png?text=Error");
        }
    }
}