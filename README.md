# ByteUP JEI 1.2.0 — Zero mods no cliente

Projeto de um único plugin Bukkit/Purpur 26.3. A interface usa a tela nativa de Dialog do Minecraft e um resource pack próprio gerado e servido pelo próprio plugin. Não usa MCGUI, Glyphwork, HUDEngine nem qualquer plugin adicional.

## O que esta versão implementa

- Interface bitmap desenhada em pixels, com estrutura inspirada nas referências JEI enviadas: abas de métodos no topo, painel central com duas receitas 3×3 e painel lateral de itens em 7 colunas × 9 linhas.
- Ícones de materiais desenhados usando arquivos de textura que já existem no resource pack vanilla do cliente; o ZIP do plugin não redistribui os arquivos de textura da Mojang.
- Catálogo de itens com pesquisa por texto, paginação, tooltip e clique no item.
- Pesquisa por caixa de texto nativa do Dialog (não depende de digitar um comando no chat).
- Navegação entre receitas, visualização de receitas e de usos, filtros de crafting, fornalha, defumador, fabricação de poções e ferraria.
- Índice de receitas construído a partir da API Bukkit/Paper disponível no servidor.
- Atlas de GUI próprio em `src/main/resources/assets/byteup/textures/gui/jei_screen.png` e hitboxes invisíveis para receber cliques.
- Resource pack gerado em `plugins/ByteUP-JEI/byteup-jei-resource-pack.zip` e servido por um HTTP interno do próprio plugin.

## Limite importante do cliente vanilla

Sem código cliente/mod, o servidor não consegue inserir elementos na tela de inventário vanilla aberta pela tecla **E** nem detectar diretamente essa tecla. Por isso o ByteUP JEI abre como uma tela Dialog nativa do Minecraft 26.3, usando o renderer de fonte bitmap para desenhar o catálogo/receitas. A estrutura visual é reproduzida dentro dessa tela própria; não é uma injeção na tela E original.

## Compilar no Windows

Requisitos:
- JDK 25 (o script detecta `java.specification.version`, sem interpretar o texto de `java -version` como número).
- Gradle instalado e disponível no `PATH`.
- Acesso à internet na primeira compilação para baixar a Paper API 26.3.

Na pasta extraída, execute:

```powershell
.\compilar.bat
```

Saída esperada: `build\libs\ByteUP-JEI-1.2.2.jar`.

## Instalar no Purpur 26.3

1. Copie `ByteUP-JEI-1.2.2.jar` para `plugins/`.
2. Inicie o servidor uma vez. O plugin gera o resource pack e cria `plugins/ByteUP-JEI/config.yml`.
3. Edite `resource-pack.public-url` para um URL público acessível pelo cliente, terminando em `/byteup-jei.zip`. Exemplo: `http://mc.seudominio.com:8767/byteup-jei.zip`.
4. Libere a porta TCP configurada em `resource-pack.port` (padrão `8767`) no firewall/host. Se o provedor não permitir portas extras, publique o ZIP gerado por um host HTTP/HTTPS externo e coloque a URL externa em `public-url`.
5. Reinicie o servidor. O pack é oferecido automaticamente quando o jogador entra; com `required: true`, o jogador precisa aceitá-lo.
6. Use `/jei`.

O plugin não consegue adivinhar seu IP/domínio público; esse URL precisa ser informado pelo dono/host do servidor. O resource pack contém apenas os glifos e as texturas gráficas ByteUP e referencia as texturas vanilla existentes no cliente.

## Comandos

- `/jei` — abre o catálogo.
- `/jei <texto>` — inicia a pesquisa no catálogo.
- `/jei item <MATERIAL>` — abre as receitas do item.
- `/jei uses <MATERIAL>` — mostra onde o item é usado.
- `/jei reload` — reconstrói índice e resource pack (permissão `byteupjei.admin`).

## Observações sobre receitas

As receitas são obtidas por `Bukkit.recipeIterator()`. O plugin normaliza os tipos nativos conhecidos. Em receitas shaped/shapeless, as opções possíveis de cada ingrediente são indexadas para pesquisa de usos; na imagem, é exibida uma opção representativa por slot. Receitas especiais adicionadas por outros plugins só aparecem quando esses plugins as registram na API de receitas Bukkit/Paper.

## Texturas (onde ficam e como editar)

- `src/main/resources/assets/byteup/textures/gui/jei_screen.png` — atlas 660x370 com TODO o visual (abas, painel de receitas, painel de itens, slots, setas, busca). Nao tem texto: titulos, numeros e nomes sao desenhados pelo plugin com a fonte do jogo.
- `.../gui/sel.png` (destaque da aba ativa) e `.../gui/placeholder.png` (item sem textura).
- `tools/gen_atlas.py` gera os tres arquivos (`python tools/gen_atlas.py`). As coordenadas ficam em `ui/Layout.java` e precisam bater com o script.
- Icones dos itens: copiados do client.jar da Mojang para o pack na hora da montagem (nao vao no jar do plugin).

## Como abre

- Mesa de trabalho (agachado = mesa normal) — `open-on-crafting-table`.
- Agachado + tecla de trocar de mao (F, ou a que o jogador configurou) — `open-on-sneak-swap-hand`.
- `/jei` continua existindo.
- A tecla E nao e visivel para o servidor (limite do protocolo).

Dica: a tela tem 660x370 px de GUI; em 1080p use "Escala da interface" 2.
