module othello.othello {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires org.kordamp.bootstrapfx.core;

    opens othello.gui to javafx.fxml;
    exports othello.gui;
    exports othello.game;
    opens othello.game to javafx.fxml;
    exports szte.mi;
    opens szte.mi to javafx.fxml;
    exports othello.othelloAi;
    opens othello.othelloAi to javafx.fxml;
}