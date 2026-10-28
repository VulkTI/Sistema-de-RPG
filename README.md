# Sistema RPG

Mod Fabric para Minecraft 26.3 com progressão RPG e afixos orientados a dados.

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
