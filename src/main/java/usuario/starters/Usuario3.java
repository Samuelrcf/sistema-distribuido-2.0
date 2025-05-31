package usuario.starters;

import usuario.Usuario;

public class Usuario3 {
    public static void main(String[] args) {
        Usuario usuario = new Usuario("localhost", 5000);
        usuario.solicitarDados(); 
    }
}