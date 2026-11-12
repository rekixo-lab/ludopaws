extends Control

const MenuTile = preload("res://scripts/ui/components/menu_tile.gd")

func select_route(route: StringName) -> bool:
	var selected: MenuTile
	for child in get_children():
		if child is MenuTile and child.get_meta("route", &"") == route:
			selected = child
			break
	if selected == null:
		return false

	for child in get_children():
		if child is MenuTile:
			child.current = child == selected
	return true
