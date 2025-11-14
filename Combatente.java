public class Combatente {
    String nome;
    int pv, pvMax, pa, paMax;

    public Combatente(String nome, int pvMax, int paInicio, int paMax) {
        this.nome = nome;
        this.pvMax = pvMax;
        this.pv = pvMax;
        this.pa = paInicio;
        this.paMax = paMax;
    }

    public boolean vivo() {
        return pv > 0;
    }
}
