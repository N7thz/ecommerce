package br.com.serratec.ecommerce.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.serratec.ecommerce.model.Usuario;
import br.com.serratec.ecommerce.service.UsuarioService;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {
    
    @Autowired
    private UsuarioService usuarioService;

    @GetMapping
    public ResponseEntity<List<Usuario>> obterTodos(){
        return ResponseEntity.ok(usuarioService.obterTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Usuario> obterPorId(@PathVariable Long id){
        return ResponseEntity.ok(usuarioService.obterPorId(id));
    }

    @PostMapping
    public ResponseEntity<Usuario> adicionar(@RequestBody Usuario usuario){
        Usuario titularAdicionado = usuarioService.adicionar(usuario);

        return ResponseEntity
            .status(201)
            .body(titularAdicionado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Usuario> atualizar(@PathVariable Long id, @RequestBody Usuario usuario){
        Usuario titularAtualizado = usuarioService.atualizar(id, usuario);

        return ResponseEntity
            .status(200)
            .body(titularAtualizado);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletar(@PathVariable Long id){
        usuarioService.deletar(id);
        
        return ResponseEntity
            .status(204)
            .build();
    }

}
