package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.scene.input.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Aplicação JavaFX que gerencia uma lista de remédios, com:
 *  - Coluna de Checkbox para marcar "tomado"
 *  - Data/Hora de tomada
 *  - Botão discreto para remover
 *  - Reordenação de itens via Drag-and-Drop
 *  - Colunas não reordenáveis
 *  - Botões "Sobre" e "Sair"
 *  - Tamanho fixo, não redimensionável
 */
public class Main extends Application {

    // Lista observável de remédios
    private final ObservableList<Remedio> listaRemedios = FXCollections.observableArrayList();

    // TextArea que exibe os remédios tomados
    private final TextArea txtAreaRemediosTomados = new TextArea();

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Gerenciador de Remédios");
        primaryStage.setResizable(false); // (6) Não pode ser redimensionado

        // ========== 1. Seção para Adicionar Remédios ==========
        Label lblTitulo = new Label("Lista de Remédios");
        lblTitulo.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        Label lblNome = new Label("Nome do Remédio:");
        TextField txtNome = new TextField();
        txtNome.setPromptText("Ex.: Dipirona, Ibuprofeno...");

        Label lblObs = new Label("Observações:");
        TextField txtObs = new TextField();
        txtObs.setPromptText("Ex.: Tomar após refeição");

        Button btnAdicionar = new Button("Adicionar Remédio");

        HBox boxInserir = new HBox(10, lblNome, txtNome, lblObs, txtObs, btnAdicionar);
        boxInserir.setAlignment(Pos.CENTER_LEFT);

        // ========== 2. Tabela de Remédios ==========
        TableView<Remedio> tableRemedios = new TableView<>(listaRemedios);
        tableRemedios.setPrefHeight(250);
        tableRemedios.setEditable(true); // Precisamos disso para o checkbox

        // Coluna: Nome
        TableColumn<Remedio, String> colNome = new TableColumn<>("Remédio");
        colNome.setCellValueFactory(cell -> cell.getValue().nomeProperty());
        colNome.setPrefWidth(150);

        // Coluna: Observações
        TableColumn<Remedio, String> colObs = new TableColumn<>("Observações");
        colObs.setCellValueFactory(cell -> cell.getValue().observacoesProperty());
        colObs.setPrefWidth(200);

        // Coluna: Tomado? (Checkbox)
        TableColumn<Remedio, Boolean> colTomado = new TableColumn<>("Tomado?");
        colTomado.setCellValueFactory(cell -> cell.getValue().tomadoProperty());
        colTomado.setCellFactory(CheckBoxTableCell.forTableColumn(index -> {
            // Retorna a BooleanProperty para cada linha
            return listaRemedios.get(index).tomadoProperty();
        }));
        colTomado.setPrefWidth(80);
        colTomado.setEditable(true);

        // Coluna: Data/Hora Tomada
        TableColumn<Remedio, String> colDataHora = new TableColumn<>("Data/Hora Tomada");
        colDataHora.setCellValueFactory(cell -> cell.getValue().dataHoraTomadoProperty());
        colDataHora.setPrefWidth(180);

        // (3) Botão de remover discreto, em coluna vazia (sem título)
        TableColumn<Remedio, Void> colRemover = new TableColumn<>("");
        colRemover.setPrefWidth(50);
        colRemover.setCellFactory(param -> new TableCell<>() {
            private final Button btnRemove = new Button("X");

            {
                // Ao clicar em "X", remove o item
                btnRemove.setOnAction(e -> {
                    Remedio remedio = getTableView().getItems().get(getIndex());
                    listaRemedios.remove(remedio);
                    atualizaListaTomados();
                });
                // Ajuste de estilo discreto (opcional)
                btnRemove.setStyle("-fx-font-weight: bold;");
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(btnRemove);
                }
            }
        });

        // Adicionando colunas à tabela
        tableRemedios.getColumns().addAll(colNome, colObs, colTomado, colDataHora, colRemover);

        // (2) Impedir reordenamento das colunas pelo usuário
        tableRemedios.getColumns().forEach(col -> col.setReorderable(false));

        // ========== 3. Label e TextArea de Remédios Tomados ==========
        Label lblTomados = new Label("Remédios Tomados:");
        lblTomados.setStyle("-fx-font-weight: bold;");

        txtAreaRemediosTomados.setEditable(false);
        txtAreaRemediosTomados.setPrefHeight(100);

        // ========== 4. Drag & Drop para reordenar linhas ==========
        tableRemedios.setRowFactory(tv -> {
            TableRow<Remedio> row = new TableRow<>();

            // Ao iniciar o arraste (DragDetected), guardamos o índice
            row.setOnDragDetected(event -> {
                if (!row.isEmpty()) {
                    int index = row.getIndex();
                    Dragboard db = row.startDragAndDrop(TransferMode.MOVE);
                    ClipboardContent cc = new ClipboardContent();
                    cc.putString(String.valueOf(index));
                    db.setContent(cc);
                    event.consume();
                }
            });

            // Ao arrastar sobre outra linha (DragOver), validamos se podemos soltar
            row.setOnDragOver(event -> {
                Dragboard db = event.getDragboard();
                if (db.hasString()) {
                    int draggedIndex = Integer.parseInt(db.getString());
                    int thisIndex = row.getIndex();
                    if (draggedIndex != thisIndex) {
                        event.acceptTransferModes(TransferMode.MOVE);
                    }
                }
                event.consume();
            });

            // Soltando (DragDropped): movemos o item na lista
            row.setOnDragDropped(event -> {
                Dragboard db = event.getDragboard();
                boolean success = false;
                if (db.hasString()) {
                    int draggedIndex = Integer.parseInt(db.getString());
                    Remedio draggedItem = tableRemedios.getItems().remove(draggedIndex);

                    int dropIndex;
                    if (row.isEmpty()) {
                        // Soltou abaixo de todas as linhas
                        dropIndex = tableRemedios.getItems().size();
                    } else {
                        dropIndex = row.getIndex();
                    }
                    tableRemedios.getItems().add(dropIndex, draggedItem);
                    tableRemedios.getSelectionModel().clearAndSelect(dropIndex);
                    success = true;
                }
                event.setDropCompleted(success);
                event.consume();
            });
            return row;
        });

        // ========== 5. Botões Sobre e Sair ==========
        Button btnSobre = new Button("Sobre");
        btnSobre.setOnAction(e -> {
            Alert alertaSobre = new Alert(Alert.AlertType.INFORMATION);
            alertaSobre.setTitle("Sobre");
            alertaSobre.setHeaderText("Informações");
            alertaSobre.setContentText("Desenvolvido por Daniel Azevedo de Oliveira Maia\n"
                    + "Todos os direitos reservados.");
            alertaSobre.showAndWait();
        });

        Button btnSair = new Button("Sair");
        btnSair.setOnAction(e -> Platform.exit());

        // Dispor "Sobre" e "Sair" em lados opostos, se desejado:
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox boxBotoesFinais = new HBox(10, btnSobre, spacer, btnSair);
        boxBotoesFinais.setAlignment(Pos.CENTER);

        // ========== 6. Eventos de adicionar e "tomado" ==========
        btnAdicionar.setOnAction(event -> {
            String nome = txtNome.getText().trim();
            String observacao = txtObs.getText().trim();

            if (nome.isEmpty()) {
                Alert alerta = new Alert(Alert.AlertType.WARNING);
                alerta.setTitle("Aviso");
                alerta.setHeaderText("Nome do remédio vazio");
                alerta.setContentText("Por favor, insira um nome para o remédio.");
                alerta.showAndWait();
                return;
            }

            Remedio novo = new Remedio(nome, observacao);
            listaRemedios.add(novo);

            txtNome.clear();
            txtObs.clear();
        });

        // Listener para cada Remedio adicionado, disparando mudança em "tomado"
        listaRemedios.addListener((ListChangeListener<Remedio>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (Remedio r : change.getAddedSubList()) {
                        r.tomadoProperty().addListener(criaListenerTomado(r));
                    }
                }
            }
        });

        // ========== 7. Layout principal ==========
        VBox vboxMain = new VBox(15);
        vboxMain.setPadding(new Insets(20));

        // Parte de cima: título e form de inserir
        vboxMain.getChildren().addAll(lblTitulo, boxInserir);
        // Tabela
        vboxMain.getChildren().add(tableRemedios);
        // Label e textarea
        vboxMain.getChildren().addAll(lblTomados, txtAreaRemediosTomados);
        // Botões finais (Sobre, Sair)
        vboxMain.getChildren().add(boxBotoesFinais);

        Scene scene = new Scene(vboxMain, 900, 500);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    /**
     * Retorna um listener que, quando "tomado" muda:
     * 1) Se true, salva data/hora atual
     * 2) Se false, limpa data/hora
     * 3) Atualiza lista de tomados
     * 4) Se todos tomados, mostra mensagem de sucesso
     */
    private ChangeListener<Boolean> criaListenerTomado(Remedio remedio) {
        return (obs, oldVal, newVal) -> {
            if (newVal) {
                // Marcou como tomado
                LocalDateTime agora = LocalDateTime.now();
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
                remedio.setDataHoraTomado(agora.format(fmt));
            } else {
                // Desmarcou
                remedio.setDataHoraTomado("");
            }
            atualizaListaTomados();

            if (todosTomados()) {
                Alert alerta = new Alert(Alert.AlertType.INFORMATION);
                alerta.setTitle("Sucesso");
                alerta.setHeaderText("Todos os remédios foram tomados!");
                alerta.setContentText("Parabéns, você tomou todos os remédios cadastrados.");
                alerta.showAndWait();
            }
        };
    }

    /**
     * Atualiza a exibição dos remédios tomados no TextArea.
     */
    private void atualizaListaTomados() {
        StringBuilder sb = new StringBuilder("Remédios tomados:\n");
        for (Remedio r : listaRemedios) {
            if (r.isTomado()) {
                sb.append(" - ").append(r.getNome());
                if (!r.getDataHoraTomado().isBlank()) {
                    sb.append(" (").append(r.getDataHoraTomado()).append(")");
                }
                if (!r.getObservacoes().isBlank()) {
                    sb.append(" [Obs: ").append(r.getObservacoes()).append("]");
                }
                sb.append("\n");
            }
        }
        txtAreaRemediosTomados.setText(sb.toString());
    }

    /**
     * Verifica se a lista não está vazia e se todos os Remedios estão marcados como tomados.
     */
    private boolean todosTomados() {
        if (listaRemedios.isEmpty()) return false;
        for (Remedio r : listaRemedios) {
            if (!r.isTomado()) return false;
        }
        return true;
    }

    /**
     * Classe interna representando o Remédio
     */
    public static class Remedio {
        private final StringProperty nome = new SimpleStringProperty();
        private final StringProperty observacoes = new SimpleStringProperty();
        private final BooleanProperty tomado = new SimpleBooleanProperty(false);
        private final StringProperty dataHoraTomado = new SimpleStringProperty("");

        public Remedio(String nome, String obs) {
            this.nome.set(nome);
            this.observacoes.set(obs);
        }

        public String getNome() { return nome.get(); }
        public void setNome(String value) { nome.set(value); }
        public StringProperty nomeProperty() { return nome; }

        public String getObservacoes() { return observacoes.get(); }
        public void setObservacoes(String value) { observacoes.set(value); }
        public StringProperty observacoesProperty() { return observacoes; }

        public boolean isTomado() { return tomado.get(); }
        public void setTomado(boolean value) { tomado.set(value); }
        public BooleanProperty tomadoProperty() { return tomado; }

        public String getDataHoraTomado() { return dataHoraTomado.get(); }
        public void setDataHoraTomado(String value) { dataHoraTomado.set(value); }
        public StringProperty dataHoraTomadoProperty() { return dataHoraTomado; }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
