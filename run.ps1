# Compile
javac `
  --module-path "C:\Users\zafri\Downloads\Taska Attendance System\javafx-sdk-21.0.9\lib" `
  --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.media `
  -cp "C:\Users\zafri\Downloads\Taska Attendance System\jar_files\*;src" `
  -d bin src/nfc/*.java

# Run
java `
  --module-path "C:\Users\zafri\Downloads\Taska Attendance System\javafx-sdk-21.0.9\lib" `
  --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.media `
  -cp "C:\Users\zafri\Downloads\Taska Attendance System\jar_files\*;bin" `
  nfc.LoginView