package othello.gui;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import othello.othelloAi.OthelloModel;
import othello.othelloAi.AiPlayer;
import szte.mi.Move;

import java.net.URL;
import java.util.*;

public class OthelloGuiController implements Initializable {
    private static final int NUM_SQUARES = 64;
    @FXML
    private Label playerTurn;
    @FXML
    private Label gameStatus;
    @FXML
    private GridPane gameBoard = new GridPane();
    private final OthelloModel game;
    private final Button[] fields;
    private int[] possibleMoves;
    private AiPlayer ai;

    public OthelloGuiController() {
        this.game = new OthelloModel();
        this.possibleMoves = new int[0];
        this.fields = new Button[NUM_SQUARES];
        this.ai = new AiPlayer();
    }

    @FXML
    protected void onResetButtonClick() {
        this.game.reset();
        ai.init(1,0,new Random());
        this.setupBoard();
        this.updateUI();

    }

    private void setupBoard() {
        this.gameBoard.getChildren().clear();
        for (int i = 0; i < OthelloModel.BOARD_SIZE; i++) {
            for (int j = 0; j < OthelloModel.BOARD_SIZE; j++) {
                Button b = new Button(" ");
                int squareIndex = i * OthelloModel.BOARD_SIZE + j;
                this.fields[squareIndex] = b;
                this.setBtnColor(squareIndex);
                b.setPrefSize(100, 100);
                this.gameBoard.add(b, j, i);
            }
        }
    }

    private void setBtnColor(int squareIndex){
        fields[squareIndex].setStyle("-fx-background-color: " + this.game.getSquareColor(squareIndex) + ";");
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        this.onResetButtonClick();
        gameBoard.setHgap(5);
        gameBoard.setVgap(5);
    }

    private void updateUI(){
        // revert to old color
        for (int index: this.possibleMoves) {
            this.setBtnColor(index);
            this.fields[index].setOnAction(null);
        }
        this.possibleMoves = game.getIndexMoves();
        if (possibleMoves.length == 0){
            if(game.isRunning()) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Warning Dialog " + game.getCurrentPlayer());
                alert.setHeaderText("You have to pass, there are not options left for you...");
                alert.setContentText("Careful with the next step!");

                alert.showAndWait();
                game.makeMove(0);
                updateUI();
            }else {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setHeaderText(game.gameStatus());
                alert.showAndWait();
            }
        }
        for (int i = 0; i < fields.length; i++) {
            setBtnColor(i);
        }
        // mark possible moves blue
        for (int i = 0; i < possibleMoves.length; i++) {
            int finalI = i;
            int index = possibleMoves[i];
            fields[index].setStyle("-fx-background-color: blue");
            fields[index].setOnAction(event -> {
                this.game.makeMove(finalI);
                this.updateUI();});
        }

        gameStatus.setText(game.gameStatus());
        if (game.isRunning()) {
            playerTurn.setText("It is " + game.getCurrentPlayer() + "'s turn");
            if (game.getCurrent() == OthelloModel.PLAYER_WHITE){
                int cell = (int) game.getLastCellChanged();
                int row = cell / OthelloModel.BOARD_SIZE;
                int col = cell - row * OthelloModel.BOARD_SIZE;
                Move aiMove = ai.nextMove(new Move(col, row),0,0);
                if (aiMove != null && game.isRunning()) {
                    int cellIndex = aiMove.y * OthelloModel.BOARD_SIZE + aiMove.x;
                    int[] m = game.getIndexMoves();
                    for (int i = 0; i < m.length; i++) {
                        if (m[i] == cellIndex) {
                            game.makeMove(i);
                            break;
                        }
                    }
                } else if (aiMove == null && game.isRunning()) {
                    game.makeMove(0);
                }
                updateUI();
            }
        }
        else {
            playerTurn.setText("");
        }
    }


}
