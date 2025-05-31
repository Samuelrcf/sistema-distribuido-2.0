package service.starters;

import service.Servidor;

public class Servidor1 {
    public static void main(String[] args) {
        Servidor s1 = new Servidor(6001, 7001, 1);
        s1.iniciar();
        System.out.println("Servidor 1 iniciado");
    }
}
