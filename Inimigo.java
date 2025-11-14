public class Inimigo extends Combatente {
    int danoMin, danoMax;
    double chanceAcerto;

    public Inimigo(String nome, int pvMax, int danoMin, int danoMax, double chanceAcerto) {
        super(nome, pvMax, 0, 0);
        this.danoMin = danoMin;
        this.danoMax = danoMax;
        this.chanceAcerto = chanceAcerto;
    }
}
