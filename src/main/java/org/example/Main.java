package org.example;


import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.*;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class Main extends Application {

    private static final String DATA_FILE = "remedios.json";
    private final ObservableList<Remedio> listaRemedios = FXCollections.observableArrayList();
    private final FilteredList<Remedio> filteredRemedios = new FilteredList<>(listaRemedios);
    private final Deque<Remedio> undoStack = new ArrayDeque<>();

    private TextField txtPesquisa = new TextField();
    private TextArea txtAreaHistorico = new TextArea();
    private TextArea txtAreaRemediosTomados = new TextArea();

    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("Gerenciador de Remédios Avançado");
        primaryStage.setResizable(true);

        carregarDados();

        VBox mainLayout = criarLayoutPrincipal();
        Scene scene = new Scene(mainLayout, 1000, 700);
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> salvarDados());
        primaryStage.show();
    }

    private VBox criarLayoutPrincipal() {
        VBox vboxMain = new VBox(15);
        vboxMain.setPadding(new Insets(20));

        // Cabeçalho
        Label lblTitulo = new Label("Gerenciador de Remédios");
        lblTitulo.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        // Barra de ferramentas
        HBox toolbar = criarBarraFerramentas();

        // Seção de entrada de dados
        HBox boxEntrada = criarSecaoEntradaDados();

        // Tabela
        TableView<Remedio> tableRemedios = criarTabelaRemedios();

        // Áreas de texto
        VBox boxAreasTexto = new VBox(10,
                criarLabelArea("Histórico de Ações:", txtAreaHistorico),
                criarLabelArea("Remédios Tomados:", txtAreaRemediosTomados));

        // Botões inferiores
        HBox boxBotoes = criarBotoesInferiores();

        vboxMain.getChildren().addAll(
                lblTitulo, toolbar, boxEntrada,
                tableRemedios, boxAreasTexto, boxBotoes);

        return vboxMain;
    }

    private HBox criarBarraFerramentas() {
        Button btnExportar = new Button("Exportar");
        btnExportar.setOnAction(e -> exportarDados());

        Button btnImportar = new Button("Importar");
        btnImportar.setOnAction(e -> importarDados());

        Button btnUndo = new Button("Desfazer (Ctrl+Z)");
        btnUndo.setOnAction(e -> desfazerUltimaAcao());

        txtPesquisa.setPromptText("Pesquisar remédios...");
        txtPesquisa.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredRemedios.setPredicate(remedio ->
                    remedio.getNome().toLowerCase().contains(newVal.toLowerCase()) ||
                            remedio.getObservacoes().toLowerCase().contains(newVal.toLowerCase()));
        });

        return new HBox(10, btnExportar, btnImportar, btnUndo,
                new Label("Pesquisar:"), txtPesquisa);
    }

    private HBox criarSecaoEntradaDados() {
        TextField txtNome = new TextField();
        txtNome.setPromptText("Nome do remédio");
        TextField txtObs = new TextField();
        txtObs.setPromptText("Observações");
        ComboBox<String> cbDosagem = new ComboBox<>(
                FXCollections.observableArrayList("1 comprimido", "2 comprimidos",
                        "5ml", "10ml", "1 gota", "2 gotas"));
        cbDosagem.setPromptText("Dosagem");

        ComboBox<String> cbFrequencia = new ComboBox<>(
                FXCollections.observableArrayList("Diário", "12/12h", "8/8h",
                        "Semanal", "Quando necessário"));
        cbFrequencia.setPromptText("Frequência");

        Button btnAdicionar = new Button("Adicionar");
        btnAdicionar.setOnAction(e -> {
            adicionarRemedio(txtNome.getText().trim(),
                    txtObs.getText().trim(),
                    cbDosagem.getValue(),
                    cbFrequencia.getValue());
            txtNome.clear();
            txtObs.clear();
            cbDosagem.getSelectionModel().clearSelection();
            cbFrequencia.getSelectionModel().clearSelection();
        });

        HBox boxEntrada = new HBox(10);
        boxEntrada.getChildren().addAll(
                new Label("Nome:"), txtNome,
                new Label("Dosagem:"), cbDosagem,
                new Label("Frequência:"), cbFrequencia,
                new Label("Obs:"), txtObs, btnAdicionar);
        boxEntrada.setAlignment(Pos.CENTER_LEFT);
        return boxEntrada;
    }

    private TableView<Remedio> criarTabelaRemedios() {
        TableView<Remedio> table = new TableView<>(filteredRemedios);

        // 1) Ativar a redimensionamento automático:
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        table.setEditable(true);
        table.setPrefHeight(300);

        // Colunas
        TableColumn<Remedio, String> colNome = new TableColumn<>("Remédio");
        colNome.setCellValueFactory(cell -> cell.getValue().nomeProperty());
        colNome.setPrefWidth(150);

        TableColumn<Remedio, String> colDosagem = new TableColumn<>("Dosagem");
        colDosagem.setCellValueFactory(cell -> cell.getValue().dosagemProperty());
        colDosagem.setCellFactory(ComboBoxTableCell.forTableColumn(
                FXCollections.observableArrayList("1 comprimido", "2 comprimidos",
                        "5ml", "10ml", "1 gota", "2 gotas")));
        colDosagem.setPrefWidth(120);

        TableColumn<Remedio, String> colFrequencia = new TableColumn<>("Frequência");
        colFrequencia.setCellValueFactory(cell -> cell.getValue().frequenciaProperty());
        colFrequencia.setCellFactory(ComboBoxTableCell.forTableColumn(
                FXCollections.observableArrayList("Diário", "12/12h", "8/8h",
                        "Semanal", "Quando necessário")));
        colFrequencia.setPrefWidth(120);

        TableColumn<Remedio, String> colObs = new TableColumn<>("Observações");
        colObs.setCellValueFactory(cell -> cell.getValue().observacoesProperty());
        colObs.setPrefWidth(200);

        TableColumn<Remedio, Boolean> colTomado = new TableColumn<>("Tomado");
        colTomado.setCellValueFactory(cell -> cell.getValue().tomadoProperty());
        colTomado.setCellFactory(CheckBoxTableCell.forTableColumn(colTomado));
        colTomado.setPrefWidth(80);

        TableColumn<Remedio, String> colDataHora = new TableColumn<>("Última Tomada");
        colDataHora.setCellValueFactory(cell -> cell.getValue().dataHoraTomadoProperty());
        colDataHora.setPrefWidth(160);

        TableColumn<Remedio, Void> colAcoes = new TableColumn<>("Ações");
        colAcoes.setPrefWidth(150);
        colAcoes.setCellFactory(param -> new TableCell<>() {
            private final Button btnRemover = new Button("Remover");
            private final Button btnEditar = new Button("Editar");

            {
                btnRemover.setStyle("-fx-text-fill: red;");
                btnRemover.setOnAction(e -> removerRemedio(getIndex()));

                btnEditar.setOnAction(e -> editarRemedio(getIndex()));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    HBox box = new HBox(5, btnEditar, btnRemover);
                    box.setAlignment(Pos.CENTER);
                    setGraphic(box);
                }
            }
        });

        table.getColumns().addAll(colNome, colDosagem, colFrequencia,
                colObs, colTomado, colDataHora, colAcoes);
        table.getColumns().forEach(col -> col.setReorderable(false));

        configurarDragAndDrop(table);
        configurarAtalhosTeclado(table);

        return table;
    }

    private HBox criarBotoesInferiores() {
        Button btnSobre = new Button("Sobre");
        btnSobre.setOnAction(e -> mostrarSobre());

        Button btnEstatisticas = new Button("Estatísticas");
        btnEstatisticas.setOnAction(e -> mostrarEstatisticas());

        Button btnSair = new Button("Sair");
        btnSair.setOnAction(e -> Platform.exit());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        return new HBox(10, btnSobre, btnEstatisticas, spacer, btnSair);
    }

    private void adicionarRemedio(String nome, String observ, String dosagem, String frequencia) {
        if (nome.isEmpty()) {
            mostrarAlerta("Erro", "Nome do remédio não pode estar vazio!");
            return;
        }

        if (listaRemedios.stream().anyMatch(r -> r.getNome().equalsIgnoreCase(nome))) {
            mostrarAlerta("Erro", "Já existe um remédio com este nome!");
            return;
        }

        Remedio novo = new Remedio(nome, observ, dosagem, frequencia);
        listaRemedios.add(novo);
        adicionarHistorico("Adicionado: " + nome);

        novo.tomadoProperty().addListener((obs, oldVal, newVal) -> {
            atualizarStatusTomado(novo, newVal);
            atualizarListas();
        });
    }

    private void removerRemedio(int index) {
        if (index >= 0 && index < listaRemedios.size()) {
            Remedio removido = listaRemedios.remove(index);
            undoStack.push(removido);
            adicionarHistorico("Removido: " + removido.getNome());
            atualizarListas();
        }
    }

    private void desfazerUltimaAcao() {
        if (!undoStack.isEmpty()) {
            Remedio restaurado = undoStack.pop();
            listaRemedios.add(restaurado);
            adicionarHistorico("Desfeita remoção de: " + restaurado.getNome());
            atualizarListas();
        }
    }

    private void editarRemedio(int index) {
        Remedio selecionado = listaRemedios.get(index);
        Dialog<Remedio> dialog = new Dialog<>();
        dialog.setTitle("Editar Remédio");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        TextField txtNome = new TextField(selecionado.getNome());
        TextField txtObs = new TextField(selecionado.getObservacoes());
        ComboBox<String> cbDosagem = new ComboBox<>(
                FXCollections.observableArrayList("1 comprimido", "2 comprimidos", "5ml", "10ml", "1 gota", "2 gotas"));
        cbDosagem.setValue(selecionado.getDosagem());
        ComboBox<String> cbFrequencia = new ComboBox<>(
                FXCollections.observableArrayList("Diário", "12/12h", "8/8h", "Semanal", "Quando necessário"));
        cbFrequencia.setValue(selecionado.getFrequencia());

        grid.addRow(0, new Label("Nome:"), txtNome);
        grid.addRow(1, new Label("Dosagem:"), cbDosagem);
        grid.addRow(2, new Label("Frequência:"), cbFrequencia);
        grid.addRow(3, new Label("Observações:"), txtObs);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                selecionado.setNome(txtNome.getText());
                selecionado.setObservacoes(txtObs.getText());
                selecionado.setDosagem(cbDosagem.getValue());
                selecionado.setFrequencia(cbFrequencia.getValue());
                adicionarHistorico("Editado: " + selecionado.getNome());
                return selecionado;
            }
            return null;
        });

        dialog.showAndWait();
    }

    private void atualizarStatusTomado(Remedio remedio, boolean tomado) {
        if (tomado) {
            remedio.setDataHoraTomado(LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
            adicionarHistorico("Marcado como tomado: " + remedio.getNome());
        } else {
            remedio.setDataHoraTomado("");
            adicionarHistorico("Desmarcado: " + remedio.getNome());
        }

        if (todosTomados()) {
            mostrarAlerta("Parabéns!", "Todos os remédios foram tomados!");
        }
    }

    private void atualizarListas() {
        StringBuilder tomados = new StringBuilder();
        listaRemedios.stream()
                .filter(Remedio::isTomado)
                .forEach(r -> tomados.append(r.toString()).append("\n"));
        txtAreaRemediosTomados.setText(tomados.toString());
    }

    private boolean todosTomados() {
        return !listaRemedios.isEmpty() &&
                listaRemedios.stream().allMatch(Remedio::isTomado);
    }

    private void configurarDragAndDrop(TableView<Remedio> table) {
        table.setRowFactory(tv -> {
            TableRow<Remedio> row = new TableRow<>();

            row.setOnDragDetected(event -> {
                if (!row.isEmpty()) {
                    Dragboard db = row.startDragAndDrop(TransferMode.MOVE);
                    ClipboardContent cc = new ClipboardContent();
                    cc.putString(String.valueOf(row.getIndex()));
                    db.setContent(cc);
                    event.consume();
                }
            });

            row.setOnDragOver(event -> {
                Dragboard db = event.getDragboard();
                if (db.hasString()) {
                    event.acceptTransferModes(TransferMode.MOVE);
                    event.consume();
                }
            });

            row.setOnDragDropped(event -> {
                Dragboard db = event.getDragboard();
                if (db.hasString()) {
                    int draggedIndex = Integer.parseInt(db.getString());
                    Remedio draggedItem = listaRemedios.remove(draggedIndex);

                    int dropIndex = row.isEmpty() ? listaRemedios.size() : row.getIndex();
                    listaRemedios.add(dropIndex, draggedItem);

                    event.setDropCompleted(true);
                    event.consume();
                }
            });
            return row;
        });
    }

    private void configurarAtalhosTeclado(TableView<Remedio> table) {
        table.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.Z) {
                desfazerUltimaAcao();
            }
            if (event.getCode() == KeyCode.DELETE) {
                removerRemedio(table.getSelectionModel().getSelectedIndex());
            }
        });
    }

    private void mostrarSobre() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Sobre");
        alert.setHeaderText("Gerenciador de Remédios v2.0");
        alert.setContentText("Desenvolvido por Daniel Azevedo\n"
                        + "Recursos principais:\n"
                        + "- Persistência de dados\n"
                        + "- Histórico de ações\n"
                        + "- Edição de registros\n"
                        + "- Exportação/Importação de dados\n"
                        + "- Sistema de desfazer ações");
                alert.showAndWait();
    }

    private void mostrarEstatisticas() {
        long total = listaRemedios.size();
        long tomados = listaRemedios.stream().filter(Remedio::isTomado).count();

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Estatísticas");
        alert.setHeaderText("Resumo do Uso");
        alert.setContentText(String.format(
                "Total de remédios: %d\n"
                        + "Tomados: %d\n"
                        + "Pendentes: %d\n"
                        + "Taxa de conclusão: %.1f%%",
                total, tomados, total - tomados,
                (total > 0 ? (tomados * 100.0 / total) : 0)));
        alert.showAndWait();
    }

    private void exportarDados() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Exportar Dados");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        File file = fileChooser.showSaveDialog(primaryStage);

        if (file != null) {
            try (Writer writer = new FileWriter(file)) {
                new Gson().toJson(listaRemedios, writer);
                adicionarHistorico("Dados exportados para: " + file.getName());
            } catch (IOException ex) {
                mostrarAlerta("Erro", "Falha ao exportar dados: " + ex.getMessage());
            }
        }
    }

    private void importarDados() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Importar Dados");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        File file = fileChooser.showOpenDialog(primaryStage);

        if (file != null) {
            try (Reader reader = new FileReader(file)) {
                List<Remedio> importados = new Gson().fromJson(reader,
                        new TypeToken<List<Remedio>>(){}.getType());
                listaRemedios.setAll(importados);
                adicionarHistorico("Dados importados de: " + file.getName());
            } catch (IOException ex) {
                mostrarAlerta("Erro", "Falha ao importar dados: " + ex.getMessage());
            }
        }
    }

    private void carregarDados() {
        File file = new File(DATA_FILE);
        if (file.exists()) {
            try (Reader reader = new FileReader(file)) {
                List<Remedio> carregados = new Gson().fromJson(reader,
                        new TypeToken<List<Remedio>>(){}.getType());
                listaRemedios.setAll(carregados);
                adicionarHistorico("Dados carregados automaticamente");
            } catch (IOException ex) {
                mostrarAlerta("Erro", "Falha ao carregar dados: " + ex.getMessage());
            }
        }
    }

    private void salvarDados() {
        try (Writer writer = new FileWriter(DATA_FILE)) {
            new Gson().toJson(listaRemedios, writer);
            adicionarHistorico("Dados salvos automaticamente");
        } catch (IOException ex) {
            mostrarAlerta("Erro", "Falha ao salvar dados: " + ex.getMessage());
        }
    }

    private void adicionarHistorico(String mensagem) {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        txtAreaHistorico.appendText("[" + timestamp + "] " + mensagem + "\n");
    }

    private void mostrarAlerta(String titulo, String mensagem) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensagem);
        alert.showAndWait();
    }

    private VBox criarLabelArea(String label, TextArea area) {
        area.setEditable(false);
        area.setWrapText(true);
        VBox box = new VBox(5, new Label(label), area);
        box.setPrefHeight(150);
        return box;
    }

    public static class Remedio implements Serializable {
        private final StringProperty nome = new SimpleStringProperty();
        private final StringProperty observacoes = new SimpleStringProperty();
        private final StringProperty dosagem = new SimpleStringProperty();
        private final StringProperty frequencia = new SimpleStringProperty();
        private final BooleanProperty tomado = new SimpleBooleanProperty(false);
        private final StringProperty dataHoraTomado = new SimpleStringProperty("");

        public Remedio(String nome, String obs, String dosagem, String frequencia) {
            this.nome.set(nome);
            this.observacoes.set(obs);
            this.dosagem.set(dosagem);
            this.frequencia.set(frequencia);
        }

        // Getters, setters e properties
        public String getNome() { return nome.get(); }
        public void setNome(String value) { nome.set(value); }
        public StringProperty nomeProperty() { return nome; }

        public String getObservacoes() { return observacoes.get(); }
        public void setObservacoes(String value) { observacoes.set(value); }
        public StringProperty observacoesProperty() { return observacoes; }

        public String getDosagem() { return dosagem.get(); }
        public void setDosagem(String value) { dosagem.set(value); }
        public StringProperty dosagemProperty() { return dosagem; }

        public String getFrequencia() { return frequencia.get(); }
        public void setFrequencia(String value) { frequencia.set(value); }
        public StringProperty frequenciaProperty() { return frequencia; }

        public boolean isTomado() { return tomado.get(); }
        public void setTomado(boolean value) { tomado.set(value); }
        public BooleanProperty tomadoProperty() { return tomado; }

        public String getDataHoraTomado() { return dataHoraTomado.get(); }
        public void setDataHoraTomado(String value) { dataHoraTomado.set(value); }
        public StringProperty dataHoraTomadoProperty() { return dataHoraTomado; }

        @Override
        public String toString() {
            return String.format("%s - %s (%s) %s",
                    nome.get(), dosagem.get(), frequencia.get(),
                    tomado.get() ? "Tomado: " + dataHoraTomado.get() : "Pendente");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}