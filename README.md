# Sistema RPG

Mod Fabric para Minecraft 26.3 com progressão RPG e afixos orientados a dados.

## Funcionalidades do mod

O mod reúne um sistema de RPG persistente no mundo, com:

- classes de personagem;
- XP geral do jogador e XP separado por classe;
- níveis, pontos de habilidade e árvore de habilidades;
- perks carregadas por JSON;
- menu de status e árvore de habilidades no cliente;
- sincronização dos dados RPG entre servidor e cliente;
- sistema de afixos para armas, ferramentas e armaduras;
- rolagem automática de afixos em resultados de crafting;
- atributo `rpgsystem:affix_luck` para favorecer raridades maiores.

## Classes, XP e progressão

O mod possui atualmente a classe `Aventureiro`. Ela recebe 10 XP de classe ao derrotar uma criatura hostil e possui a seguinte árvore de habilidades:

- `vigor`: habilidade passiva que aumenta a vitalidade;
- `golpe_firme`: requer `vigor` e fortalece ataques corpo a corpo;
- `recuperacao`: requer `vigor` e recupera vida após derrotar uma criatura hostil;
- `lenda_viva`: requer `golpe_firme` e `recuperacao` e representa o ápice da árvore.

Cada nível de classe fornece 2 pontos de habilidade. As habilidades precisam respeitar seus pré-requisitos e são armazenadas por jogador. A progressão é salva no `SavedData` do mundo, associada ao UUID do jogador, portanto não depende apenas da sessão atual.

O XP usa uma curva configurável no código: o primeiro nível exige 100 XP e cada nível seguinte aumenta o custo em 50 XP. O nível máximo matemático é 1.000.000.

## Como criar uma classe

Classes são implementadas em Java porque podem definir regras próprias de XP, perks e habilidades.

### 1. Crie a classe Java

Crie uma classe em `src/main/java/br/com/rpgsystem/classes/` que estenda `RpgClass`:

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
    @Override public List<SkillDefinition> skillTree() {
        return List.of(new SkillDefinition(
            "forca", "Força", "Aumenta o dano corpo a corpo.",
            1, SkillType.PASSIVE, List.of(), "melee_damage", 2.0));
    }
}
```

Os métodos definem o identificador, nome, descrição, perks, XP concedido por ação e árvore de habilidades. O `id()` deve ser único e normalmente usa `RpgMod.id("guerreiro")`.

### 2. Registre a classe

No método `onInitialize()` de `RpgMod`, adicione:

```java
ClassRegistry.register(new GuerreiroClass());
```

O nome usado no comando será o caminho do ID:

```text
/rpg class set guerreiro
```

### 3. Configure a árvore de habilidades

Cada `SkillDefinition` recebe: ID, nome, descrição, custo, tipo, dependências, efeito e quantidade:

```java
new SkillDefinition(
    "golpe_forte", "Golpe Forte", "Fortalece ataques.",
    2, SkillType.PASSIVE, List.of("forca"), "melee_damage", 1.0
)
```

Uma lista vazia de dependências cria uma habilidade inicial. Com dependências, o jogador precisa desbloquear as habilidades indicadas antes.

### 4. Adicione perks por JSON

Crie `src/main/resources/data/rpgsystem/perks/furia.json`:

```json
{
  "name": "Fúria",
  "description": "Aumenta o dano após derrotar criaturas.",
  "effect": "melee_damage",
  "amount": 2.0
}
```

Associe-a à classe:

```java
private static final Identifier FURIA = RpgMod.id("furia");
@Override public List<Identifier> perkIds() { return List.of(FURIA); }
```

### 5. Compile e teste

Execute `./gradlew.bat build`, entre no jogo e use `/rpg class set guerreiro`. Abra `/rpg menu` para conferir a classe, XP, pontos e árvore. Use `/rpg class reset` para removê-la.

## Menu e telas

Use `/rpg menu` para abrir o menu RPG. A interface mostra o perfil do jogador, a classe selecionada, XP, nível, pontos disponíveis e a árvore de habilidades. A comunicação entre servidor e cliente usa pacotes próprios para sincronizar o snapshot do perfil.

## Comandos RPG

### Classe

```text
/rpg class set aventureiro
/rpg class reset
/rpg menu
```

### XP

Os comandos de XP exigem permissão de moderador. Podem operar em pontos ou níveis:

```text
/rpg xp player add 100 points
/rpg xp player remove 1 levels
/rpg xp player set 500 points
/rpg xp class add 1 levels
/rpg xp class set 100 points
```

Também é possível informar jogadores como alvo:

```text
/rpg xp player add <jogador> 100 points
/rpg xp class add <jogador> 1 levels
```

Para zerar os dados RPG de um ou mais jogadores:

```text
/rpg reset <jogador>
```

Esse comando remove classe, XP, pontos e habilidades desbloqueadas do alvo.

## Como criar um afixo

### 1. Crie o arquivo JSON

Crie um arquivo em `src/main/resources/data/<namespace>/affixes/<id_do_afixo>.json`.
Por exemplo: `src/main/resources/data/rpgsystem/affixes/afiado.json`.
O nome do arquivo vira o ID do afixo (`rpgsystem:afiado`).

### 2. Preencha a estrutura obrigatória

```json
{
  "name": "Afiado",
  "rarity": "raro",
  "groups": ["Swords"],
  "modifiers": [
    {
      "attribute": "minecraft:attack_damage",
      "amount": 3.0,
      "operation": "add_value",
      "slot": "mainhand",
      "description": "Aumenta o dano de ataque"
    }
  ]
}
```

Campos: `name` é o nome exibido; `rarity` é a raridade; `groups` define os itens permitidos; e `modifiers` define os atributos aplicados. Cada modificador exige `attribute`, `amount`, `operation`, `slot` e `description`. A descrição é obrigatória e fica registrada na definição do afixo, mas não é exibida no tooltip atual.

As operações aceitas são `add_value`, `add_multiplied_base` e `add_multiplied_total`. Para porcentagem, use uma operação multiplicada: `0.10` representa 10%.

### 3. Escolha a raridade

Use exatamente: `comum`, `incomum`, `raro`, `epico`, `lendario` ou `mitico`.

| Valor | Nome | Cor |
|---|---|---|
| `comum` | Comum | Cinza |
| `incomum` | Incomum | Verde-limão |
| `raro` | Raro | Azul-claro |
| `epico` | Épico | Roxo |
| `lendario` | Lendário | Dourado em negrito |
| `mitico` | Mítico | Vermelho em negrito |

A raridade altera a chance de rolagem, a cor do nome do item e a cor da linha do afixo.

### 4. Relacione o afixo aos grupos de itens

Os grupos principais são `Swords`, `Spears`, `Pickaxes`, `Axes`, `Shovels`, `Hoes`, `Helmets`, `Chestplates`, `Leggings`, `Boots` e `Armors`. Eles contêm as ferramentas, armas e armaduras das variantes listadas no mod.

Também existem grupos de material:

- `Metal`: itens feitos de cobre, ferro, ouro, diamante ou netherita que pertencem aos grupos principais.
- `Leather`: itens de couro dos grupos principais.

Um afixo pode ter vários grupos, e basta o item pertencer a um deles:

```json
"groups": ["Swords", "Axes"]
```

Também é possível escolher itens específicos diretamente pelo ID registrado no Minecraft. O campo `items` aceita um ou mais IDs completos, incluindo itens vanilla e itens de outros mods:

```json
{
  "name": "Tridente do Mar",
  "rarity": "epico",
  "items": ["minecraft:trident", "minecraft:diamond_sword"],
  "modifiers": [
    {
      "attribute": "minecraft:attack_damage",
      "amount": 5.0,
      "operation": "add_value",
      "slot": "mainhand",
      "description": "Aumenta o dano"
    }
  ]
}
```

O campo `items` é opcional. Um afixo precisa ter pelo menos um `group` ou um `item`. Se `groups` e `items` forem usados juntos, eles funcionam como alternativas: o item pode corresponder a qualquer grupo ou a qualquer ID específico.

Use sempre o ID completo no formato `namespace:item`, por exemplo `minecraft:trident` ou `meu_mod:espada_solar`.

## Grupos definidos por JSON

Além dos grupos internos (`Swords`, `Metal`, `Leather` etc.), você pode criar grupos reutilizáveis em arquivos dentro de:

```text
data/<namespace>/affix_groups/<nome_do_grupo>.json
```

O nome do arquivo vira o nome do grupo. Por exemplo, `armas_especiais.json` pode ser usado como `Armas Especiais` ou `armas_especiais` no JSON do afixo.

Um grupo pode listar itens diretamente:

```json
{
  "items": [
    "minecraft:trident",
    "minecraft:diamond_sword",
    "meu_mod:espada_solar"
  ]
}
```

Também pode reunir outros grupos:

```json
{
  "groups": ["Swords", "Metal"]
}
```

Os itens listados em `items` e nos grupos referenciados são unidos. Para fazer interseção, use dois grupos separados por espaço no afixo:

```json
"groups": ["Metal Armas Especiais"]
```

Nesse caso, somente itens que pertencem simultaneamente a `Metal` e `Armas Especiais` serão aceitos. Várias entradas na lista continuam sendo alternativas (`OU`):

```json
"groups": ["Metal Swords", "Armas Especiais"]
```

Isso significa `(Metal ∩ Swords) OU Armas Especiais`. Grupos JSON podem referenciar outros grupos, mas referências cíclicas são ignoradas para evitar loops.

Depois de adicionar ou alterar grupos, execute `/reload` para recarregar os arquivos.

Para exigir a interseção de material e categoria, use os nomes separados por espaço. Por exemplo:

```json
"groups": ["Leather Armors"]
```

Esse afixo só poderá ser aplicado a armaduras de couro. Outros exemplos são `Metal Swords` para espadas metálicas e `Metal Pickaxes` para picaretas metálicas. O material vem primeiro e o grupo principal depois.

### 5. Recarregue os arquivos

Depois de criar ou alterar um JSON, execute `/reload` no jogo. O afixo só será rolado para itens compatíveis com seus grupos.

## Testando no Minecraft

1. Compile com `./gradlew.bat build` ou instale o JAR de `build/libs` no perfil Fabric 26.3.
2. Segure um item compatível.
3. Use `/rpg affix apply rpgsystem:teste_afixo` para aplicar o teste diretamente.
4. O tooltip deverá mostrar, por exemplo, `Afixo: Veloz [Raro]`.
5. Use `/rpg affix roll` para sortear um afixo compatível.
6. Use `/rpg affix remove` para remover o afixo do item na mão.

Itens criados por crafting também podem receber um afixo automaticamente quando o resultado é retirado da bancada, se existir algum afixo compatível carregado.

## Sorteio e chance de raridade

O atributo `rpgsystem:affix_luck` começa com valor `1`. Valores maiores favorecem raridades mais altas:

```text
/attribute @s rpgsystem:affix_luck base set 5
```

Esse atributo afeta o sorteio, mas não altera os grupos permitidos nem força um afixo específico.
