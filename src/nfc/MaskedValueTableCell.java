package nfc;

import java.util.function.Function;

import javafx.geometry.Pos;

final class MaskedValueTableCell<S> extends CopyableTableCell<S> {
    MaskedValueTableCell(
        Function<S, String> fullValueProvider,
        Function<String, String> maskFormatter,
        Function<S, String> rowSummaryProvider,
        Function<S, String> recordIdProvider,
        Function<S, String> jsonProvider,
        Pos alignment
    ) {
        super(
            fullValueProvider,
            maskFormatter == null ? value -> SummaryTableSupport.maskMiddle(value, 2, 2) : maskFormatter,
            rowSummaryProvider,
            recordIdProvider,
            jsonProvider,
            alignment,
            false
        );
    }
}