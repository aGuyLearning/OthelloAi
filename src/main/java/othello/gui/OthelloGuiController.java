package othello.gui;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import othello.game.OthelloModel;

import java.net.URL;
import java.util.*;

public class OthelloGuiController implements Initializable, Observer {
    @FXML
    private Label playerTurn;
    @FXML
    private Label gameStatus;
    @FXML
    private GridPane gameBoard = new GridPane();
    private final OthelloModel game;
    private final Button[][] fields;
    private final ArrayList<Move> possibleMoves;

    public OthelloGuiController() {
        this.game = new OthelloModel();
        game.addObserver(this);
        this.possibleMoves = new ArrayList<>();
        this.fields = new Button[OthelloModel.BOARD_SIZE][OthelloModel.BOARD_SIZE];
    }

    @FXML
    protected void onResetButtonClick() {
        this.game.reset();
        this.setupBoard();
        this.updateUI();

    }

    private void setupBoard() {
        this.gameBoard.getChildren().clear();
        for (int i = 0; i < OthelloModel.BOARD_SIZE; i++) {
            for (int j = 0; j < OthelloModel.BOARD_SIZE; j++) {
                Button b = new Button(" ");
                // lambdas need "final" variables
                int row = i;
                int col = j;

                this.fields[row][col] = b;
                this.setBtnColor(row, col);
                b.setOnAction(actionEvent -> this.game.move(row, col)
                );
                b.setPrefSize(100, 100);
                this.gameBoard.add(b, j, i);
            }
        }
    }

    private void setBtnColor(int row, int col){
        fields[row][col].setStyle("-fx-background-color: " + this.game.getFieldColor(row, col) + ";");
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        this.onResetButtonClick();
        gameBoard.setHgap(5);
        gameBoard.setVgap(5);
    }
    private void updateUI(){
        for (Move move: this.possibleMoves) {
            this.setBtnColor(move.x, move.y);
        }
        this.possibleMoves.clear();
        for (Move move : this.game.getPossibleMovesCurrentPlayer()){
            fields[move.x][move.y].setStyle("-fx-background-color: blue");
            possibleMoves.add(move);
        }
        gameStatus.setText(game.gameStatus());
        playerTurn.setText("It is " + game.getCurrentPlayerLabel() + "'s turn");
    }

    @Override
    public void update(Observable o, Object arg) {
        if (o.equals(game)) {
            for (Move flip: (ArrayList<Move>) arg) {
                fields[flip.x][flip.y].setOnAction(null);
                this.setBtnColor(flip.x,flip.y);
            }
            this.updateUI();
        }

    }
}
