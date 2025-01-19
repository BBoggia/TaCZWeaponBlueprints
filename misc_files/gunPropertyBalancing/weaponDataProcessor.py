"""
Script Name: weaponDataProcessor.py

Description:
This script processes weapon data files for a Minecraft mod. It extracts weapon properties,
updates weapon attributes based on ammo type, and outputs weapon statistics for analysis.

Functions:
- Extracts and cleans JSON weapon data.
- Filters weapons by type.
- Updates weapon damage based on ammo type.
- Retrieves and cleans weapon names.
- Prints weapon statistics.

Usage:
Run this script to update weapon damages and review weapon stats.
"""

import os
import json
import re

def remove_comments_from_json(raw_json):
    """
    Removes single-line and multi-line comments from a JSON string.
    """
    # Define regex patterns for removing comments
    regex_single_line_comments = r"[\n\r]\s*\/\/.*"
    regex_multi_line_comments = r"(?<=[\n\r])\s*\/\*(.[\n\r]?)*?\*\/\s*?"
    regex_inline_multi_line_comments = r"(\".+?(?<!\\\\)\"\s*,?)\s*\/\*(.[\n\r]?)*?\*\/\s*?"
    regex_inline_single_line_comments = r"(\".+?(?<!\\\\)\"\s*,?)\s*\/\/.*?(?=[\n\r])"
    regex_last_multiline_comment = r"/\*[^(?!\*/)]*\*/"
    regex_empty_lines = r"\n\s*\n"

    # Remove comments using regex patterns
    raw_json = re.sub(regex_single_line_comments, '', raw_json, flags=re.MULTILINE)
    raw_json = re.sub(regex_multi_line_comments, '', raw_json, flags=re.MULTILINE)
    raw_json = re.sub(regex_inline_multi_line_comments, r"\1", raw_json, flags=re.MULTILINE)
    raw_json = re.sub(regex_inline_single_line_comments, r"\1", raw_json, flags=re.MULTILINE)
    raw_json = re.sub(regex_last_multiline_comment, '', raw_json, flags=re.MULTILINE)
    raw_json = re.sub(regex_empty_lines, '\n', raw_json, flags=re.MULTILINE)

    return raw_json

def find_data_and_index_files(root_dir):
    """
    Traverses the root directory to find all weapon data and index files.

    Parameters:
    - root_dir (str): The root directory to search.

    Returns:
    - data_files (list): List of paths to weapon data files.
    - index_files (list): List of paths to index files.
    """
    data_files = []
    index_files = []

    mod_dirs = [d for d in os.listdir(root_dir) if os.path.isdir(os.path.join(root_dir, d))]
    for mod_dir in mod_dirs:
        mod_path = os.path.join(root_dir, mod_dir)
        for dirpath, dirnames, filenames in os.walk(mod_path):
            # Find data files in 'guns/data' directories
            if 'guns' in dirnames:
                guns_dir = os.path.join(dirpath, 'guns')

                # Data files
                data_dir = os.path.join(guns_dir, 'data')
                if os.path.isdir(data_dir):
                    try:
                        files = [f for f in os.listdir(data_dir) if f.endswith('.json')]
                        files = [os.path.join(data_dir, f) for f in files]
                        data_files.extend(files)
                    except Exception as e:
                        print(f"Error accessing {data_dir}: {e}")
                else:
                    print(f"Data directory does not exist: {data_dir}")

                # Index files
                index_dir = os.path.join(guns_dir, 'index')
                if os.path.isdir(index_dir):
                    try:
                        files = [f for f in os.listdir(index_dir) if f.endswith('.json')]
                        files = [os.path.join(index_dir, f) for f in files]
                        index_files.extend(files)
                    except Exception as e:
                        print(f"Error accessing {index_dir}: {e}")
                else:
                    print(f"Index directory does not exist: {index_dir}")

    return data_files, index_files

def load_item_data_keys(index_files):
    """
    Loads item data keys from index files.

    Parameters:
    - index_files (list): List of paths to index files.

    Returns:
    - item_data_keys (dict): Dictionary mapping item IDs to their data.
    """
    item_data_keys = {}
    for file in index_files:
        with open(file, 'r') as f:
            try:
                raw_data = remove_comments_from_json(f.read())
                data = json.loads(raw_data)
                temp_item_data = {}

                if 'data' in data:
                    temp_item_data['dataKey'] = data['data']
                    key = temp_item_data['dataKey'].split(':')[0] + ":" + os.path.splitext(os.path.basename(file))[0]
                else:
                    continue

                if 'name' in data:
                    temp_item_data['nameKey'] = data['name']
                else:
                    continue

                if 'type' in data:
                    temp_item_data['type'] = data['type']
                else:
                    temp_item_data['type'] = 'ammo'

                data_dir = os.path.abspath(os.path.join(os.path.dirname(os.path.dirname(file)), 'data'))
                temp_item_data['dataDir'] = data_dir

                item_data_keys[key] = temp_item_data

            except Exception as e:
                print(f"Error loading {file}: {e}")

    return item_data_keys

def extract_weapon_data(item_data_keys, selected_type):
    """
    Extracts weapon data for weapons of the selected type.

    Parameters:
    - item_data_keys (dict): Dictionary of item data keys.
    - selected_type (str): The weapon type to filter by.

    Outputs:
    - Prints weapon information to the console.
    """
    
    output_data = {}
    
    for key, value in item_data_keys.items():
        # Skip items that don't match the selected type
        if "type" in value and value["type"] != selected_type:
            continue

        # Construct path to the language file for weapon names
        name_path = os.path.join(os.path.dirname(os.path.dirname(value["dataDir"])), "lang", "en_us.json")
        if not os.path.isfile(name_path):
            print(f"Name file does not exist: {name_path}")
            continue

        # Load and clean the weapon name
        with open(name_path, 'r') as f:
            try:
                raw_data = remove_comments_from_json(f.read())
                data = json.loads(raw_data)
                name = re.sub(r'§.', '', data[value['nameKey']] if "." in value['nameKey'] else value['nameKey'])
                item_data_keys[key]['name'] = name
            except Exception as e:
                print(f"Error loading name from {name_path}: {e}")
                continue

        # Construct path to the weapon data file
        data_file_path = os.path.join(value['dataDir'], value['dataKey'].split(":")[1] + ".json")
        if not os.path.isfile(data_file_path):
            print(f"Data file does not exist: {data_file_path}")
            continue

        # Load and process weapon data
        with open(data_file_path, 'r') as f:
            try:
                raw_data = f.read()
                raw_data = remove_comments_from_json(raw_data)
                data = json.loads(raw_data)
                # Extract weapon properties
                weapon_info = {
                    # "id": key,
                    # "name": re.sub(r'§.', '', name),
                    "ammo": data.get('ammo', ''),
                    "damage": data.get('bullet', {}).get('damage', ''),
                    "rpm": data.get('rpm', ''),
                    "armor_ignore": data.get('bullet', {}).get('extra_damage', {}).get('armor_ignore', ''),
                    "head_shot_multiplier": data.get('bullet', {}).get('extra_damage', {}).get('head_shot_multiplier', '')
                }
                output_data[key] = weapon_info
                # Output weapon information
                # print(f"{weapon_info},")
            except Exception as e:
                print(f"3Error loading {data_file_path}: {e} - {raw_data}")
                exit()
                continue
    return output_data

def update_weapon_damage(weapon_data, ammo_data):
    """
    Updates weapon damage based on ammo type.

    Parameters:
    - weapon_data (dict): Dictionary of weapon data.
    - ammo_data (dict): Dictionary of ammo data.

    Outputs:
    - Prints updated
    """
    
    for weapon in weapon_data:
        # Skip weapons without ammo data
        if weapon_data[weapon]['ammo'] not in ammo_data:
            continue

        # Update weapon damage based on ammo type
        weapon_data[weapon]['damage'] = ammo_data[weapon_data[weapon]['ammo']]
        
        del weapon_data[weapon]['ammo']

        # Output updated weapon information
        print(f"Updated {weapon} damage to {weapon_data[weapon]['damage']}")
        
    return weapon_data
        
ammo_data_path = '/Users/bransonboggia/Documents/Personal Documents/minecraft_stuff/custom_mods/TaCZWeaponBlueprints/misc_files/gunPropertyBalancing/ammoDamageData.json'
        
ammo_damage_mapping = {}

with open(ammo_data_path, 'r') as f:
    try:
        ammo_data = json.loads(f.read())
        for ammo in ammo_data:
            ammo_damage_mapping[ammo] = ammo_data[ammo]['damage']
    except Exception as e:
        print(f"Error loading {ammo_data_path}: {e}")

def main():
    # Define the root directory (update '/path/to/your/root_dir' to your actual path)
    # root_dir = '/Users/bransonboggia/Documents/Personal Documents/minecraft_stuff/custom_mods/TaCZWeaponBlueprints/run/config/tacz/custom'
    root_dir = '/Users/bransonboggia/Documents/Personal Documents/minecraft_stuff/custom_mods/TaCZWeaponBlueprints/misc_files/tacz/custom'

    # Specify the selected weapon type (e.g., 'shotgun', 'rifle')
    possible_types = ["shotgun", "rifle", "pistol", "sniper", "smg", "mg", "rpg"]
    # selected_type = "shotgun"
    
    new_data = {}

    # Step 1: Find data and index files
    data_files, index_files = find_data_and_index_files(root_dir)

    # Step 2: Load item data keys from index files
    item_data_keys = load_item_data_keys(index_files)

    # Step 3: Extract and output weapon data
    for type in possible_types:
        new_data[type] = extract_weapon_data(item_data_keys, type)
        
    # Step 4: Update weapon damage based on ammo type
    for type in possible_types:
        new_data[type] = update_weapon_damage(new_data[type], ammo_damage_mapping)
        
    # Step 4: Output weapon data
    print(new_data)

if __name__ == "__main__":
    main()