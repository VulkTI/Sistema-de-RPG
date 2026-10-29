# Sistema RPG

Mod para Fabric em Minecraft 26.3 que adiciona progressão de jogador e de classe, perks e afixos configuráveis por recursos JSON.

## Requisitos e instalação

- Minecraft 26.3
- Fabric Loader 0.20.1 ou superior e Fabric API
- Java 25 ou superior

Para compilar o projeto no Windows, execute `./gradlew.bat build` (ou `compilar.bat`). O JAR fica em `build/libs`. Instale-o junto com Fabric API na pasta `mods` do perfil Fabric. O mod funciona nos ambientes cliente e servidor; dados de progressão são controlados pelo servidor.

## Conteúdo atual

- XP e nível do jogador, além de XP, nível e pontos de habilidade por classe. Perfis são salvos no mundo por UUID do jogador.
- Seleção de classe no primeiro acesso, tela de status e tela genérica para árvore de habilidades.
- Duas classes registradas: **Ferreiro**, voltado a fundição e fabricação, e **Minerador**, voltado a escavação e coleta de minérios.
- Sistema de afixos em armas, ferramentas, armaduras e outros itens compatíveis: modificadores de atributos, raridades, grupos, tags, itens específicos, tooltips e rolagem automática.
- Herança de afixo ao aprimorar um item na ferraria; se o item-base não tiver afixo, o resultado pode receber um novo.
- Atributo `rpgsystem:affix_luck`, comandos de administração e sincronização dos dados entre servidor e cliente.

O XP do jogador aumenta em 8 ao derrotar uma criatura da categoria MONSTER. A classe ativa pode receber XP adicional conforme a ação. O Ferreiro recebe XP ao coletar certos lingotes/nuggets fundidos e ao fabricar itens metálicos. A curva de XP começa em 100 para avançar do nível 1, aumentando 5 XP mais a metade do quadrado do número de níveis já avançados; o nível máximo é 100. Cada novo nível de classe concede 2 pontos de habilidade. A classe Ferreiro atual retorna uma árvore vazia, então não há habilidades para desbloquear nela no momento.

O botão **P** abre a tela de status. Use `/rpg menu` para escolher uma classe (também abre a seleção ao entrar sem classe). Para trocar de classe, use `/rpg class set ferreiro` ou `/rpg class set minerador`.

Os valores de XP são configurados em `config/rpgsystem_xp.json` na pasta do jogo. O arquivo é criado automaticamente na primeira inicialização do mod; edite os números e reinicie o jogo/servidor para aplicar. Os IDs dos minérios não incluem o prefixo `minecraft:`.

## Comandos

### Classe e telas

```text
/rpg menu
/rpg class set <classe>
/rpg class reset
```

`/rpg class reset` remove a classe ativa, mas preserva XP e progressos guardados. `/rpg menu` abre a seleção de classe; a tela de status também pode ser aberta com **P**.

### XP e reset de jogador

Os comandos a seguir exigem permissão de moderador. Sem alvo, a operação vale para quem executa o comando; com `<jogador>`, pode-se selecionar um ou vários jogadores aceitos pelo argumento vanilla `players`.

```text
/rpg xp player add <quantidade> points
/rpg xp player remove <quantidade> levels
/rpg xp player set <quantidade> points
/rpg xp class add <quantidade> levels
/rpg xp class remove <jogador> <quantidade> points
/rpg xp class set <quantidade> points
/rpg reset <jogador>
```

As mesmas operações de XP aceitam alvo opcional antes da quantidade, por exemplo `/rpg xp player add Steve 100 points` ou `/rpg xp class add Steve 1 levels`. `points` altera XP bruto; `levels` altera o nível, mantendo o progresso da barra quando possível. XP de classe só pode ser alterado para jogadores com uma classe ativa. `/rpg reset` apaga classe, XP, pontos e habilidades do alvo.

### Afixos

Os comandos de afixo atuam sobre o item na mão principal:

```text
/rpg affix list
/rpg affix roll
/rpg affix roll rarity <comum|incomum|raro|epico|lendario|mitico>
/rpg affix roll <id_do_afixo>
/rpg affix apply <id_do_afixo>
/rpg affix remove
```

`list` mostra os afixos compatíveis e as chances individuais estimadas na rolagem normal. `roll` faz uma rolagem normal, incluindo chance de nenhum afixo. `roll rarity` escolhe uniformemente entre afixos compatíveis daquela raridade, sem a rolagem normal de raridade/ausência. `roll <id>` simula rolagens normais até obter o afixo indicado e aplica o resultado; serve para teste e pode levar muitas rolagens simuladas. `apply` aplica diretamente um afixo compatível e `remove` retira o afixo atual.

## Classes e perks

Classes são implementadas em Java, pois definem identidade, regras de XP, perks e árvore de habilidades. A implementação atual é `FerreiroClass`, registrada em `RpgMod.onInitialize()`.

### Passo a passo: criar uma classe

1. Crie uma classe Java em `src/main/java/br/com/rpgsystem/classes/` estendendo `RpgClass`:

   ```java
   public final class GuerreiroClass extends RpgClass {
       private static final Identifier ID = RpgMod.id("guerreiro");
       @Override public Identifier id() { return ID; }
       @Override public String name() { return "Guerreiro"; }
       @Override public String description() { return "Especialista em combate corpo a corpo."; }
       @Override public List<Identifier> perkIds() { return List.of(); }
       @Override public int xpFor(RpgAction action) {
           return action == RpgAction.HOSTILE_MOB_KILL ? 10 : 0;
       }
       @Override public List<SkillDefinition> skillTree() { return List.of(); }
   }
   ```

2. Registre a classe em `RpgMod.onInitialize()`:

   ```java
   ClassRegistry.register(new GuerreiroClass());
   ```

3. O comando usa o caminho do ID, neste exemplo `/rpg class set guerreiro`. Para adicionar habilidades, retorne `SkillDefinition`s em `skillTree()`. Cada definição inclui ID, nome, descrição, custo, tipo, dependências, efeito e quantidade; o jogador precisa ter pontos suficientes e desbloquear as dependências primeiro. A tela e o armazenamento das habilidades são genéricos, mas os efeitos de habilidades precisam ser conectados às rotinas de jogo apropriadas.

### Passo a passo: criar e associar uma perk

1. Crie `src/main/resources/data/rpgsystem/perks/furia.json`:

   ```json
   {
     "name": "Fúria",
     "description": "Aumenta o dano após derrotar criaturas.",
     "effect": "melee_damage",
     "amount": 2.0
   }
   ```

2. Associe o ID do recurso à classe:

   ```java
   private static final Identifier FURIA = RpgMod.id("furia");
   @Override public List<Identifier> perkIds() { return List.of(FURIA); }
   ```

O JSON fornece nome, descrição, chave de efeito e quantidade. A interface mostra nome e descrição. Um efeito só funciona quando há código que reconhece sua chave; atualmente o tratamento de morte reconhece `heal_on_hostile_kill`, enquanto rotinas de crafting/ferraria implementam efeitos específicos do Ferreiro descritos abaixo.

### Classe Ferreiro incluída

- Recebe XP por lingotes e pepitas de cobre, ferro e ouro coletados da fundição. Cada barra vale, respectivamente, 4, 10 e 15 XP; pepitas valem um nono desses valores, arredondado para a pilha.
- Recebe XP ao fabricar lingotes de netherita e itens do grupo `Metal`. Itens metálicos também ganham 10% de durabilidade máxima. A fórmula de XP por item usa base 10, bônus por material (ferro +10, ouro +20, diamante +35, netherita +50) e mais 30% por barra de metal usada; lingotes de netherita dão adicionalmente 500 XP por unidade.
- A perk `Afixo de Mestre` reduz pela metade a chance-base de não receber afixo ao retirar resultados de crafting. A perk `Metal Reforçado` descreve durabilidade, aplicada atualmente aos resultados do grupo `Metal`. A perk `Reparação Eficiente` está descrita na interface, mas sua chave ainda não tem rotina de reparação ligada.
- Ao aprimorar na ferraria, o afixo do item-base é preservado. Sem afixo herdado, o resultado pode receber um afixo novo, com chance-base reduzida pela metade.

As perks ficam em `src/main/resources/data/rpgsystem/perks/` e são carregadas pelo ID referenciado pela classe.

### Classe Minerador incluída

- Recebe XP ao quebrar minérios vanilla sem Toque Suave, incluindo as variantes de ardosiabissal: carvão 2; cobre e quartzo 3; redstone e lápis-lazúli 4; ferro 5; ouro e ouro do Nether 6; esmeralda 12; diamante 16; e detritos ancestrais 40 XP.
- Pedra e ardosiabissal sem Toque Suave acumulam progresso; a cada 64 blocos, concedem 4 XP. Esse contador é salvo no perfil do jogador, portanto não se perde ao desconectar ou trocar de classe.
- **Picareta Conservada:** ao gastar durabilidade de uma picareta há 10% de chance adicional de não a gastar. A rolagem acontece antes da durabilidade vanilla, por isso se combina com Inquebrável.
- **Golpes Precisos:** concede 5% de velocidade global para quebrar blocos enquanto Minerador é a classe ativa.
- **Veio Rico:** ao quebrar um minério sem Toque Suave, há 1% de chance de receber um recurso mineral extra. O bônus entrega uma unidade do recurso-base do minério e não reaplica Fortuna.

## Afixos

Cada JSON de afixo é carregado como recurso de dados. O nome do arquivo forma o ID: `data/rpgsystem/affixes/afiado.json` corresponde a `rpgsystem:afiado`.

### Passo a passo: criar um afixo

1. Crie `src/main/resources/data/<namespace>/affixes/<id>.json`, por exemplo `src/main/resources/data/rpgsystem/affixes/afiado.json`.
2. Preencha os campos obrigatórios `name`, `rarity`, ao menos uma regra de compatibilidade (`groups`, `tags` ou `items`) e `modifiers`:

   ```json
   {
     "name": "Afiado",
     "description": "Uma lâmina de fio excepcional.",
     "rarity": "raro",
     "groups": ["Swords"],
     "modifiers": [
       {
         "attribute": "minecraft:attack_damage",
         "amount": 3.0,
         "operation": "add_value",
         "slot": "mainhand"
       }
     ]
   }
   ```

   `description` é opcional e aparece no tooltip quando Shift está pressionado; sem ela, descrições antigas nos modificadores são usadas como compatibilidade. Cada modificador exige `attribute`, `amount`, `operation` e `slot`. Operações válidas: `add_value`, `add_multiplied_base` e `add_multiplied_total`. Para um multiplicador de 10%, use `0.10` com uma operação multiplicada.

3. Escolha uma raridade exatamente como aparece abaixo:

   | Valor JSON | Exibição | Cor |
   |---|---|---|
   | `comum` | Comum | Cinza |
   | `incomum` | Incomum | Verde |
   | `raro` | Raro | Azul-claro |
   | `epico` | Épico | Roxo |
   | `lendario` | Lendário | Dourado e negrito |
   | `mitico` | Mítico | Vermelho e negrito |

4. Defina os itens aceitos. As listas de `groups`, `tags` e `items` são combinadas como alternativas (`OU`):

   ```json
   {
     "groups": ["Swords", "Metal"],
     "tags": ["minecraft:chest_armor"],
     "items": ["minecraft:trident", "#minecraft:foot_armor"]
   }
   ```

   `groups` aceita nomes de grupo, interseções com espaço (`"Metal Swords"`) e tags precedidas por `#`; `tags` aceita IDs de tags; `items` aceita IDs completos de itens ou tags precedidas por `#`. Dentro de cada grupo JSON também é possível combinar itens, tags e outros grupos. Confira a seção [Grupos de itens](#grupos-de-itens) para criar regras reutilizáveis.

5. Execute `/reload` para recarregar afixos e grupos de itens após editar os recursos. Para validar no jogo, segure um item compatível e use `/rpg affix list`, `/rpg affix apply <id>` ou `/rpg affix roll`. Crafting e aprimoramentos na ferraria também podem rolar afixos automaticamente ao retirar o resultado.

Os afixos aparecem no tooltip acima dos atributos vanilla. O nome e a raridade também definem a cor/nome customizado do item; a descrição única aparece ao segurar Shift.

### Grupos de itens

Os grupos são definidos em `src/main/resources/data/<namespace>/item_groups/<nome>.json`. O arquivo `metal.json`, por exemplo, pode ser referenciado como `Metal` ou `metal`. Os grupos padrão incluem armas corpo a corpo (`Swords`, `Spears`, `Axes`), ferramentas (`Pickaxes`, `Shovels`, `Hoes`), armaduras por peça (`Helmets`, `Chestplates`, `Leggings`, `Boots`), agregador `Armors` e materiais `Metal` e `Leather`.

Cada grupo pode ter `items`, `tags` e `groups` opcionais:

```json
{
  "tags": ["minecraft:swords", "c:bows"],
  "items": ["minecraft:trident", "#c:spears"],
  "groups": ["axes"]
}
```

Grupos podem referenciar outros grupos. Uma entrada com espaço exige interseção, por exemplo `"Leather Armors"` aceita itens que estejam nos dois grupos; `"Metal #minecraft:swords"` combina o grupo Metal com a tag. Entradas diferentes na lista continuam sendo alternativas. O grupo virtual `All` representa a união dos grupos JSON carregados. Após alterar os arquivos, execute `/reload`.

## Rolagem, atributo e criação de itens

Uma rolagem normal começa com 33% de chance de não gerar afixo. Com `rpgsystem:affix_luck = 0`, os pesos-base das raridades são 60, 25, 10, 4, 0.9 e 0.1, nessa ordem, normalizados entre todas as seis raridades. Se um item não tiver afixos compatíveis da raridade sorteada, a rolagem também fica vazia. Depois da raridade, um afixo compatível é escolhido uniformemente dentro daquela raridade.

O modificador interno de sorte é `max(0, 1 + affix_luck)`: valores positivos reduzem a chance de nenhum afixo e aumentam o peso relativo de raridades altas; -1 ou menos torna a chance de nenhum afixo 100%. Exemplo para testar:

```text
/attribute @s rpgsystem:affix_luck base set 1
```

Isso afeta a rolagem, sem mudar compatibilidade. A perk do Ferreiro reduz em 50% a chance-base de ausência nos resultados retirados de crafting. Na ferraria, afixos do item-base são herdados; quando não há afixo para herdar, usa-se a rolagem de afixo com a mesma redução de ausência.

## Desenvolvimento

- Código comum: `src/main/java`; código cliente: `src/client/java`.
- Recursos de dados: `src/main/resources/data/<namespace>/`, organizados em `affixes/`, `item_groups/` e `perks/`.
- Recursos cliente, idioma e configuração de mixins: `src/main/resources/assets/` e `src/main/resources/rpgsystem.mixins.json`.
- Versões de Minecraft, loader e Fabric API: `gradle.properties`; dependências e compilação: `build.gradle`.

Para iniciar o ambiente de desenvolvimento, use `./gradlew.bat runClient` ou `./gradlew.bat runServer`. Para gerar o mod, use `./gradlew.bat build`.
