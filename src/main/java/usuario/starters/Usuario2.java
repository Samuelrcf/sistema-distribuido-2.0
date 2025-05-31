package usuario.starters;

import usuario.Usuario;

public class Usuario2 {
    public static void main(String[] args) {
        Usuario usuario = new Usuario("localhost", 5000);
        usuario.solicitarDados(); 
    }
}