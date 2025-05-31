package service.starters;

import service.Servidor;

public class Servidor2 {
    public static void main(String[] args) {
        Servidor s2 = new Servidor(6002, 7002, 2);
        s2.iniciar();
        System.out.println("Servidor 2 iniciado");
    }
}
