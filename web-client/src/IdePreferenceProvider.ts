import orion = require("orion-api")

import {
  EditorStyles
  } from "api/editor"
/*
Caret row - annotationLine.currentLine.backgroundColor
 */
class IdePreferenceProvider implements orion.PreferenceProvider {
  private editorStyle: any

  constructor(style: EditorStyles) {
    var defaultFont = style.EDITOR_FONT_NAME
    var defaultFontSize = style.EDITOR_FONT_SIZE + "px"

    // Orion default editor style
    var prospecto = {
      "className": "prospecto",
      "name": "Prospecto",
      "styles": {
        "annotationLine": {
          "currentLine": {
            "backgroundColor": "#EAF2FE"
          }
        },
        "annotationRange": {
          "currentBracket": {
            "backgroundColor": "#00FE00"
          },
          "matchingBracket": {
            "backgroundColor": "#00FE00"
          },
          "matchingSearch": {
            "backgroundColor": "#c3e1ff",
            "currentSearch": {
              "backgroundColor": "#53d1ff"
            }
          },
          "writeOccurrence": {
            "backgroundColor": "#ffff00"
          }
        },
        "backgroundColor": "#ffffff",
        "color": "#151515",
        "comment": {
          "color": "#3C802C"
        },
        "constant": {
          "color": "#9932CC",
          "numeric": {
            "color": "#9932CC",
            "hex": {
              "color": "#9932CC"
            }
          }
        },
        "entity": {
          "name": {
            "color": "#98937B",
            "function": {
              "color": "#67BBB8",
              "fontWeight": "bold"
            }
          },
          "other": {
            "attribute-name": {
              "color": "#5F9EA0"
            }
          }
        },
        "fontFamily": defaultFont,
        "fontSize": defaultFontSize,
        "keyword": {
          "control": {
            "color": "#CC4C07",
            "fontWeight": "bold"
          },
          "operator": {
            "color": "#9F4177",
            "fontWeight": "bold"
          },
          "other": {
            "documentation": {
              "color": "#7F9FBF",
              "task": {
                "color": "#5595ff"
              }
            }
          }
        },
        "markup": {
          "bold": {
            "fontWeight": "bold"
          },
          "heading": {
            "color": "#0000FF"
          },
          "italic": {
            "fontStyle": "italic"
          },
          "list": {
            "color": "#CC4C07"
          },
          "other": {
            "separator": {
              "color": "#00008F"
            },
            "strikethrough": {
              "textDecoration": "line-through"
            },
            "table": {
              "color": "#3C802C"
            }
          },
          "quote": {
            "color": "#446FBD"
          },
          "raw": {
            "fontFamily": "monospace",
            "html": {
              "backgroundColor": "#E4F7EF"
            }
          },
          "underline": {
            "link": {
              "textDecoration": "underline"
            }
          }
        },
        "meta": {
          "documentation": {
            "annotation": {
              "color": "#7F9FBF"
            },
            "tag": {
              "color": "#7F7F9F"
            }
          },
          "preprocessor": {
            "color": "#A4A4A4"
          },
          "tag": {
            "color": "#CC4C07",
            "attribute": {
              "color": "#93a2aa"
            }
          }
        },
        "punctuation": {
          "operator": {
            "color":"#D1416F"
          }
        },
        "ruler": {
          "annotations": {
            "backgroundColor": "#ffffff"
          },
          "backgroundColor": "#ffffff",
          "overview": {
            "backgroundColor": "#ffffff"
          }
        },
        "rulerLines": {
          "color": "#CCCCCC"
        },
        "string": {
          "color": "#446FBD",
          "interpolated": {
            "color": "#151515"
          }
        },
        "support": {
          "type": {
            "propertyName": {
              "color": "#9F4177"
            }
          }
        },
        "textviewContent ::-moz-selection": {
          "backgroundColor": "#b4d5ff"
        },
        "textviewContent ::selection": {
          "backgroundColor": "#b4d5ff"
        },
        "textviewLeftRuler": {
          "borderColor": "#ffffff"
        },
        "textviewRightRuler": {
          "borderColor": "#ffffff"
        },
        "textviewSelection": {
          "backgroundColor": "#b4d5ff"
        },
        "textviewSelectionUnfocused": {
          "backgroundColor": "#b4d5ff"
        },
        "variable": {
          "language": {
            "color": "#7F0055",
            "fontWeight": "bold"
          },
          "other": {
            "color": "#E038AD"
          },
          "parameter": {
            "color": "#D1416F"
          }
        }
      }
    }

    // modify to get IDEA style
    prospecto.className = "intellij"
    prospecto.name = "IntelliJ Platform"
    prospecto.styles.annotationLine.currentLine.backgroundColor = style.colors.CARET_ROW_COLOR

    var selection = {backgroundColor: style.colors.SELECTION_BACKGROUND, color: "#ffffff"}
    prospecto.styles["textviewContent ::-moz-selection"] = selection
    prospecto.styles["textviewContent ::selection"] = selection
    prospecto.styles.textviewSelection = selection
    // todo: unfocused text color doesn't work
    prospecto.styles.textviewSelectionUnfocused = selection
    this.editorStyle = prospecto
  }

  get(name: string, force: boolean): any {
    if (name === "/themes") {
      //noinspection SpellCheckingInspection
      return {
        "editorstylesVersion": 999 /* orion tries to compare it with own version, but version doesn't matter in our case (and must be greater than orion verion to override) */,
        "editorstyles": [this.editorStyle]
      }
    }
    return null;
  }

  put(name: string, data: any): void {
    console.info("Ignore set " + name)
  }

  remove(name: string, key: string): void {
    console.info("Ignore remove " + name)
  }
}

export = IdePreferenceProvider