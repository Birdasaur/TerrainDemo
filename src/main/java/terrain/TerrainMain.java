package terrain;

import javafx.application.Application;

/**
 * This class exists because of the current state of JavaFX in Java11+.
 * Please see https://stackoverflow.com/questions/52569724/javafx-11-create-a-jar-file-with-gradle/52571719#52571719
 * for solution origin.
 */
public class TerrainMain {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Application.launch(App.class, args);
    }
}
