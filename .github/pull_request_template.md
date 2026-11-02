<!--
  Preencha as seções abaixo. Apague o que não se aplicar.
  Título do PR: siga a mesma convenção dos commits, por exemplo:
  feat: adiciona página de produto
  fix: corrige foco do teclado no modal
-->

## O que este PR faz

<!-- Uma ou duas frases explicando a mudança, em linguagem simples. -->

## Tipo de mudança

- [ ] `feat` funcionalidade nova
- [ ] `fix` correção de defeito
- [ ] `refactor` mudança interna, sem diferença de comportamento
- [ ] `docs` documentação

## Evidência

<!-- Captura de tela, gravação de tela ou link do ambiente publicado.
     Obrigatório sempre que a mudança afetar algo visível. -->

## Checklist do autor

- [ ] O título segue a convenção de commits convencionais
- [ ] Testei manualmente o que mudou e descrevi acima como reproduzir
- [ ] Não deixei nenhum `console.log` nem código comentado
- [ ] Não commitei nenhuma chave, senha ou URL privada
- [ ] Todo código novo em `src/` é `.ts` ou `.tsx` e não usa `any`.
- [ ] As chamadas de API estão isoladas em `src/services/` (sem `fetch` direto em componente).
- [ ] Os arrays de dependência de `useEffect` estão explícitos e corretos.
- [ ] Listas dinâmicas usam identificador único do dado como `key` (sem `key={index}`).
- [ ] Operações assíncronas têm retorno visual na interface (carregando, sucesso, erro).
- [ ] A acessibilidade básica está atendida (`<button>` semântico, `<label>`, `alt` em imagem, e a navegação por teclado funciona).
- [ ] Atualizei a documentação, se foi necessário

## Observações para quem revisa

<!-- Algo que mereça atenção especial, uma dúvida em aberto
     ou uma decisão que você queira discutir. -->