package com.barnwatch9000.ui;

import com.barnwatch9000.model.CameraDevice;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

import java.util.Optional;
import java.util.UUID;

public final class DeviceEditorDialog
{
    private DeviceEditorDialog()
    {
    }

    public static Optional<CameraDevice> show(Window owner, CameraDevice existing, int nextSortOrder)
    {
        Dialog<CameraDevice> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(existing == null ? "Add Camera" : "Edit Camera");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        TextField nameField = new TextField(existing == null ? "" : existing.name());
        TextField hostField = new TextField(existing == null ? "" : existing.host());
        TextField portField = new TextField(existing == null ? "554" : Integer.toString(existing.port()));
        TextField userField = new TextField(existing == null ? "" : existing.username());
        PasswordField passwordField = new PasswordField();
        passwordField.setText(existing == null ? "" : existing.password());
        TextField subPathField = new TextField(existing == null ? "/videoSub" : existing.subPath());
        TextField mainPathField = new TextField(existing == null ? "/videoMain" : existing.mainPath());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.addRow(0, new Label("Name"), nameField);
        grid.addRow(1, new Label("Host"), hostField);
        grid.addRow(2, new Label("Port"), portField);
        grid.addRow(3, new Label("Username"), userField);
        grid.addRow(4, new Label("Password"), passwordField);
        grid.addRow(5, new Label("Sub path"), subPathField);
        grid.addRow(6, new Label("Main path"), mainPathField);
        dialog.getDialogPane().setContent(grid);

        Node saveButton = dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String error = validate(nameField.getText(), hostField.getText(), portField.getText(), subPathField.getText(), mainPathField.getText());
            if (error != null)
            {
                event.consume();
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.initOwner(owner);
                alert.setHeaderText(null);
                alert.setTitle("Invalid Camera");
                alert.setContentText(error);
                alert.showAndWait();
            }
        });

        dialog.setResultConverter(button -> {
            if (button != saveButtonType)
            {
                return null;
            }
            return new CameraDevice(
                    existing == null ? UUID.randomUUID().toString() : existing.id(),
                    nameField.getText().trim(),
                    hostField.getText().trim(),
                    Integer.parseInt(portField.getText().trim()),
                    userField.getText().trim(),
                    passwordField.getText(),
                    subPathField.getText().trim(),
                    mainPathField.getText().trim(),
                    existing == null ? nextSortOrder : existing.sortOrder());
        });

        return dialog.showAndWait();
    }

    private static String validate(String name, String host, String portText, String subPath, String mainPath)
    {
        if (name == null || name.isBlank())
        {
            return "Name is required.";
        }
        if (host == null || host.isBlank())
        {
            return "Host is required.";
        }
        if (subPath == null || subPath.isBlank())
        {
            return "Sub path is required.";
        }
        if (mainPath == null || mainPath.isBlank())
        {
            return "Main path is required.";
        }
        try
        {
            int port = Integer.parseInt(portText.trim());
            if (port <= 0 || port > 65535)
            {
                return "Port must be between 1 and 65535.";
            }
        }
        catch (NumberFormatException ex)
        {
            return "Port must be a number.";
        }
        return null;
    }
}
