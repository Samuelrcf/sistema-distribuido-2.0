package service.starters;

import service.persistence.BancoDeDados;

public class BancoDeDados1 {
    public static void main(String[] args) {
        BancoDeDados bd1 = new BancoDeDados(6001);
        bd1.iniciar();
        System.out.println("Banco De Dados 1 iniciado.");
    }
}