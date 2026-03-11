package com.barnwatch9000.ui;

import com.barnwatch9000.db.CameraDeviceRepository;
import com.barnwatch9000.model.CameraDevice;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.sql.SQLException;
import java.util.Optional;

public final class DeviceManagerDialog
{
    private DeviceManagerDialog()
    {
    }

    public static void show(Window owner, CameraDeviceRepository repository, Runnable onChanged) throws SQLException
    {
        ObservableList<CameraDevice> items = FXCollections.observableArrayList(repository.listAll());

        ListView<CameraDevice> listView = new ListView<>(items);
        listView.setCellFactory(view -> new ListCell<>()
        {
            @Override
            protected void updateItem(CameraDevice item, boolean empty)
            {
                super.updateItem(item, empty);
                if (empty || item == null)
                {
                    setText(null);
                }
                else
                {
                    setText(item.name() + "  [" + item.host() + ":" + item.port() + "]");
                }
            }
        });

        Button addButton = new Button("Add");
        addButton.setOnAction(event -> {
            try
            {
                Optional<CameraDevice> created = DeviceEditorDialog.show(owner, null, repository.nextSortOrder());
                if (created.isPresent())
                {
                    repository.save(created.get());
                    items.setAll(repository.listAll());
                    onChanged.run();
                }
            }
            catch (SQLException ex)
            {
                showSqlError(owner, ex);
            }
        });

        Button editButton = new Button("Edit");
        editButton.disableProperty().bind(listView.getSelectionModel().selectedItemProperty().isNull());
        editButton.setOnAction(event -> {
            CameraDevice selected = listView.getSelectionModel().getSelectedItem();
            if (selected == null)
            {
                return;
            }
            try
            {
                Optional<CameraDevice> updated = DeviceEditorDialog.show(owner, selected, repository.nextSortOrder());
                if (updated.isPresent())
                {
                    repository.save(updated.get());
                    items.setAll(repository.listAll());
                    onChanged.run();
                }
            }
            catch (SQLException ex)
            {
                showSqlError(owner, ex);
            }
        });

        Button deleteButton = new Button("Delete");
        deleteButton.disableProperty().bind(listView.getSelectionModel().selectedItemProperty().isNull());
        deleteButton.setOnAction(event -> {
            CameraDevice selected = listView.getSelectionModel().getSelectedItem();
            if (selected == null)
            {
                return;
            }

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete " + selected.name() + "?", ButtonType.YES, ButtonType.CANCEL);
            confirm.initOwner(owner);
            confirm.setHeaderText(null);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.YES)
            {
                return;
            }

            try
            {
                repository.delete(selected.id());
                items.setAll(repository.listAll());
                onChanged.run();
            }
            catch (SQLException ex)
            {
                showSqlError(owner, ex);
            }
        });

        Button closeButton = new Button("Close");

        HBox buttons = new HBox(10, addButton, editButton, deleteButton, spacer(), closeButton);
        buttons.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(12, listView, buttons);
        content.setPadding(new Insets(14));
        VBox.setVgrow(listView, Priority.ALWAYS);

        BorderPane root = new BorderPane(content);
        root.setPrefSize(520, 420);

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Devices");
        stage.setScene(new Scene(root));
        closeButton.setOnAction(event -> stage.close());
        stage.showAndWait();
    }

    private static Region spacer()
    {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    private static void showSqlError(Window owner, SQLException ex)
    {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(owner);
        alert.setTitle("Database Error");
        alert.setHeaderText(null);
        alert.setContentText(ex.getMessage());
        alert.showAndWait();
    }
}
