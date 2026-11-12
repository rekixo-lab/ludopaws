extends SceneTree
## Step 1 of the reaction golden: writes tests/fixtures/reactions_gen/inputs.json (git-ignored,
## rebuilt by tests/reaction_inputs.gd). Step 2 (generate.sh) replays it through the
## unchanged live Kotlin pipeline.

const Inputs = preload("res://tests/reaction_inputs.gd")

func _init() -> void:
	var steps := Inputs.build()
	var text := Inputs.text(steps)
	var file := FileAccess.open("res://tests/fixtures/reactions_gen/inputs.json", FileAccess.WRITE)
	file.store_string(JSON.stringify({"inputHash": text.sha256_text(), "steps": steps}))
	print("wrote %d steps" % steps.size())
	quit(0)
