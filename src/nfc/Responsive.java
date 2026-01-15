package nfc;

import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;

public final class Responsive {
  private Responsive() {}

  public static void setPercentColumns(GridPane grid, double... percents) {
    grid.getColumnConstraints().clear();
    for (double p : percents) {
      ColumnConstraints cc = new ColumnConstraints();
      cc.setPercentWidth(p);
      grid.getColumnConstraints().add(cc);
    }
  }
}