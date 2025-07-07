package service.starters;

import service.CentroDeDados;

public class CentroDeDadosStarter {
    public static void main(String[] args) {
        new CentroDeDados(6001);
        System.out.println("Centro de dados iniciado.");
    }
}
