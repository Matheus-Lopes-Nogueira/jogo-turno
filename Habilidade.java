public class Habilidade {
    String nome;
    String desc;
    int custoPA;
    int danoMin, danoMax;

    public Habilidade(String nome, String desc, int custoPA, int danoMin, int danoMax) {
        this.nome = nome;
        this.desc = desc;
        this.custoPA = custoPA;
        this.danoMin = danoMin;
        this.danoMax = danoMax;
    }
}
