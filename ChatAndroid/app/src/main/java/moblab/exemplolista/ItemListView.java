package moblab.exemplolista;

public class ItemListView {

    private String texto;
    private String nome;

    public ItemListView(String texto, String nome) {
        this.texto = texto;
        this.nome = nome;
    }

    public ItemListView() {
        this.texto = "";
        this.nome = "";
    }



    public String getTexto() {
        return texto;
    }

    public void setTexto(String texto) {
        this.texto = texto;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }
}
