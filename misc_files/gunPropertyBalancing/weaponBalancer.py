"""
Script Name: weaponBalancer.py

Description:
This script balances weapon properties by adjusting damage over distance, calculates scores for weapons,
and updates weapon data accordingly.

Functions:
- Loads weapon data from JSON files.
- Calculates weapon scores based on various attributes.
- Rescales damage drop-off distances and percentages.
- Updates weapon data with new properties and scores.

Usage:
Run this script to balance weapon properties and calculate weapon scores.
"""

import os
import json

def load_json_data(file_path):
    """
    Loads JSON data from the specified file.

    Parameters:
    - file_path (str): The path to the JSON file.

    Returns:
    - data (dict): The JSON data as a dictionary.
    """
    try:
        with open(file_path, 'r') as f:
            data = json.load(f)
        return data
    except FileNotFoundError:
        print(f"File not found: {file_path}")
        return {}
    except json.JSONDecodeError as e:
        print(f"JSON decode error in {file_path}: {e}")
        return {}

def calculate_gun_score(gun):
    """
    Calculates the score of a gun based on various attributes.

    Parameters:
    - gun (dict): Dictionary containing gun properties.

    Returns:
    - normalized_score (float): Normalized score of the gun.
    """
    # Constants and weights
    MAX_DPS = 75
    MAX_ALPHA_DAMAGE = 120
    MAX_EXPLOSION_RADIUS = 5
    MAX_RANGE = 125
    MAX_PIERCE = 4
    MAX_ARMOR_IGNORE = 1.0
    MAX_HEADSHOT_MULTIPLIER = 3.5
    MAX_KNOCKBACK = 1.0
    AVERAGE_ARMOR_REDUCTION = 0.25
    MAX_RECOIL = 1.0
    MAX_INACCURACY = 6.0

    WEIGHTS = {
        'alpha_damage': 5,
        'explosion_radius': 4,
        'dps': 5,
        'range': 3,
        'recoil': 2.5,
        'inaccuracy': 2,
        'firing_mode': 2,
        'pierce': 2,
        'armor_ignore': 2,
        'headshot': 2.25,
        'knockback': 1,
        'ignite': 1
    }

    FIRING_MODE_SCORES = {
        'auto': 1.0,
        'burst': 0.7,
        'semi': 0.5
    }

    INACCURACY_WEIGHTS = {
        'stand': 0.3,
        'move': 0.3,
        'sneak': 0.1,
        'lie': 0.05,
        'aim': 0.25
    }

    # Helper functions
    def get_damage_at_distance(damage_adjust_list, distance):
        previous_entry = None
        for entry in damage_adjust_list:
            entry_distance = entry['distance']
            entry_damage = entry['damage']
            if entry_distance == "infinite":
                entry_distance = float('inf')
            if distance < entry_distance:
                if previous_entry is None:
                    return entry_damage
                else:
                    prev_dist = previous_entry['distance']
                    if prev_dist == "infinite":
                        prev_dist = float('inf')
                    prev_damage = previous_entry['damage']
                    if entry_distance - prev_dist == 0:
                        return entry_damage
                    t = (distance - prev_dist) / (entry_distance - prev_dist)
                    interpolated_damage = prev_damage + t * (entry_damage - prev_damage)
                    return interpolated_damage
            previous_entry = entry
        return damage_adjust_list[-1]['damage']

    def get_firing_mode_score(fire_modes):
        max_score = 0
        for mode in fire_modes:
            mode_score = FIRING_MODE_SCORES.get(mode.lower(), 0)
            max_score = max(max_score, mode_score)
        return max_score

    def calculate_average_recoil(recoil_data):
        total = 0
        count = 0
        for keyframe in recoil_data:
            value_range = keyframe.get('value', [0, 0])
            mean_value = sum(value_range) / len(value_range)
            total += abs(mean_value)
            count += 1
        return total / count if count > 0 else 0

    def calculate_average_inaccuracy(inaccuracy_data):
        total = 0
        for state, weight in INACCURACY_WEIGHTS.items():
            state_inaccuracy = inaccuracy_data.get(state, 0)
            total += state_inaccuracy * weight
        return total

    # Calculate properties
    bullet_info = gun.get('bullet', {})
    if 'burst_data' not in bullet_info:
        sps = gun.get('rpm', 0) / 60  # Shots per second
    else:
        burst_data = bullet_info.get('burst_data', {})
        burst_count = burst_data.get('count', 1)
        burst_delay = burst_data.get('min_interval', 0)
        burst_bpm = burst_data.get('bpm', 0)
        sps = burst_count / burst_delay * burst_bpm / 60
        rpm = gun.get('rpm', 0) / 60
        if sps < rpm:
            sps = rpm

    damage = None
    if 'extra_damage' in bullet_info and 'damage_adjust' in bullet_info['extra_damage']:
        damage_adjust_list = bullet_info.get('extra_damage', {}).get('damage_adjust', [])
        damage = get_damage_at_distance(damage_adjust_list, 30)  # Damage at 30 units
    else:
        damage = bullet_info.get('damage', 0)

    alpha_damage_score = 0
    explosion_radius_score = 0
    if 'explosion' in bullet_info:
        explosion_data = bullet_info.get('explosion', {})
        explosion_damage = explosion_data.get('damage', 0)
        alpha_damage = explosion_damage
        alpha_damage_score = min(alpha_damage / MAX_ALPHA_DAMAGE, 1)
        damage += explosion_damage

        explosion_radius = explosion_data.get('radius', 0)
        explosion_radius_score = min(explosion_radius / MAX_EXPLOSION_RADIUS, 1)

    headshot_rate = 0.25  # Assumed average headshot rate
    headshot_multiplier = bullet_info.get('extra_damage', {}).get('head_shot_multiplier', 1.0)
    armor_ignore = bullet_info.get('extra_damage', {}).get('armor_ignore', 0.0)

    # Calculate effective damage per shot
    base_damage = ((1 - headshot_rate) * damage + headshot_rate * damage * headshot_multiplier)
    adjusted_armor_reduction = AVERAGE_ARMOR_REDUCTION * (1 - armor_ignore)
    effective_damage = base_damage * (1 - adjusted_armor_reduction)
    bullet_amount = bullet_info.get('bullet_amount', 1)

    # Effective DPS
    effective_dps = effective_damage * sps * bullet_amount
    dps_score = min(effective_dps / MAX_DPS, 1)

    # Effective range
    bullet_speed = bullet_info.get('speed', 0)
    bullet_life = bullet_info.get('life', 0)
    gun_range = bullet_speed * bullet_life
    range_score = min(gun_range / MAX_RANGE, 1)

    # Other attributes
    pierce = bullet_info.get('pierce', 0)
    pierce_score = min(pierce / MAX_PIERCE, 1)

    armor_ignore_score = min(armor_ignore / MAX_ARMOR_IGNORE, 1)

    if MAX_HEADSHOT_MULTIPLIER - 1 == 0:
        headshot_score = 0
    else:
        headshot_score = (headshot_multiplier - 1) / (MAX_HEADSHOT_MULTIPLIER - 1)
    headshot_score = min(max(headshot_score, 0), 1)

    knockback = bullet_info.get('knockback', 0)
    knockback_score = min(knockback / MAX_KNOCKBACK, 1)

    ignite_score = 1 if bullet_info.get('ignite', False) else 0

    # Recoil calculation
    recoil_data = gun.get('recoil', {})
    pitch_recoil_data = recoil_data.get('pitch', [])
    yaw_recoil_data = recoil_data.get('yaw', [])

    avg_pitch_recoil = calculate_average_recoil(pitch_recoil_data)
    avg_yaw_recoil = calculate_average_recoil(yaw_recoil_data)
    total_recoil = (avg_pitch_recoil ** 2 + avg_yaw_recoil ** 2) ** 0.5

    recoil_score = 1 - (total_recoil / MAX_RECOIL)
    recoil_score = max(min(recoil_score, 1), 0)

    # Inaccuracy calculation
    inaccuracy_data = gun.get('inaccuracy', {})
    avg_inaccuracy = calculate_average_inaccuracy(inaccuracy_data)

    inaccuracy_score = 1 - (avg_inaccuracy / MAX_INACCURACY)
    inaccuracy_score = max(min(inaccuracy_score, 1), 0)

    # Firing mode calculation
    fire_modes = gun.get('fire_mode', [])
    firing_mode_score = get_firing_mode_score(fire_modes)

    # Weighted total score
    total_score = (
        dps_score * WEIGHTS['dps'] +
        range_score * WEIGHTS['range'] +
        recoil_score * WEIGHTS['recoil'] +
        inaccuracy_score * WEIGHTS['inaccuracy'] +
        firing_mode_score * WEIGHTS['firing_mode'] +
        pierce_score * WEIGHTS['pierce'] +
        armor_ignore_score * WEIGHTS['armor_ignore'] +
        headshot_score * WEIGHTS['headshot'] +
        knockback_score * WEIGHTS['knockback'] +
        ignite_score * WEIGHTS['ignite'] +
        alpha_damage_score * WEIGHTS['alpha_damage'] +
        explosion_radius_score * WEIGHTS['explosion_radius']
    )
    max_total_score = sum(WEIGHTS.values())

    normalized_score = (total_score / max_total_score) * 100  # Percentage score

    return normalized_score

def rescale_damage_dropoff(new_gun_ranges, min_max_gun_ranges):
    """
    Rescales damage drop-off distances and damage percentages for guns.

    Parameters:
    - new_gun_ranges (dict): The new gun ranges with damage drop-offs.
    - min_max_gun_ranges (dict): The minimum and maximum ranges for scaling.

    Returns:
    - new_gun_ranges (dict): Updated new gun ranges with rescaled distances.
    """
    for gun_type in new_gun_ranges:
        # Find minimum and maximum distances for the gun type
        old_distances = [entry["distance"] for gun in new_gun_ranges[gun_type] for entry in gun["damage_drop"] if entry["distance"] != "infinite"]
        old_min_distance = min(old_distances)
        old_max_distance = max(old_distances)

        for gun in new_gun_ranges[gun_type]:
            scale = (min_max_gun_ranges[gun_type]["damageFallOffEnd"] - min_max_gun_ranges[gun_type]["damageFallOffStart"]) / (old_max_distance - old_min_distance)
            rescaled_distances = [
                min_max_gun_ranges[gun_type]["damageFallOffStart"] + (d - old_min_distance) * scale
                for d in [entry["distance"] for entry in gun["damage_drop"] if entry["distance"] != "infinite"]
            ]
            for i, entry in enumerate(gun["damage_drop"]):
                if entry["distance"] != "infinite":
                    entry["distance"] = round(rescaled_distances[i], 2)
    return new_gun_ranges

def update_gun_data(gun_data, new_gun_ranges):
    """
    Updates gun data with new damage adjustments.

    Parameters:
    - gun_data (dict): Original gun data.
    - new_gun_ranges (dict): New gun ranges with rescaled distances.

    Returns:
    - gun_data (dict): Updated gun data with new damage adjustments.
    """
    for gun_type in gun_data:
        if gun_type == "rpg":
            continue
        for index, gun in enumerate(gun_data[gun_type]):
            try:
                new_damage_adjust = new_gun_ranges[gun_type][index]
                if new_damage_adjust['id'] == gun['id']:
                    for entry in new_damage_adjust['damage_drop']:
                        entry['damage'] = round(entry['damage'] * gun['bullet']['damage'], 3)
                    gun['bullet']['extra_damage']['damage_adjust'] = new_damage_adjust['damage_drop']
                else:
                    new_damage_adjust_list = [x for x in new_gun_ranges[gun_type] if x['id'] == gun['id']]
                    if not new_damage_adjust_list:
                        print(f"Error finding damage adjust for {gun['name']} - {gun_type}")
                        continue
                    new_damage_adjust = new_damage_adjust_list[0]
                    for entry in new_damage_adjust['damage_drop']:
                        entry['damage'] = round(entry['damage'] * gun['bullet']['damage'], 3)
                    gun['bullet']['extra_damage']['damage_adjust'] = new_damage_adjust['damage_drop']
            except Exception as e:
                print(f"Error updating gun data for {gun['name']} - {gun_type}: {e}")
    return gun_data

def compile_gun_scores(gun_data):
    """
    Compiles a list of guns with their calculated scores.

    Parameters:
    - gun_data (dict): The gun data after updates.

    Returns:
    - gun_score_list (dict): Dictionary of gun scores per type.
    """
    gun_score_list = {}
    for gun_type in gun_data:
        gun_score_list[gun_type] = []
        for gun in gun_data[gun_type]:
            try:
                score = calculate_gun_score(gun)
                gun_score_list[gun_type].append((gun['name'], score, gun['id'], gun_type))
            except Exception as e:
                print(f"Error calculating score for {gun['name']} - {e}")
    return gun_score_list

def normalize_and_output_scores(gun_score_list):
    """
    Normalizes the scores and outputs the combined list.

    Parameters:
    - gun_score_list (dict): Dictionary of gun scores per type.

    Outputs:
    - Prints the normalized scores as a JSON string.
    """
    combined_list = []

    # Flatten the list to find global min and max scores
    all_scores = [gun[1] for gun_type in gun_score_list for gun in gun_score_list[gun_type]]
    max_score = max(all_scores)
    min_score = min(all_scores)

    # Define new score range
    new_max = 20
    new_min = 0.4

    # Normalize scores
    for gun_type in gun_score_list:
        for gun in gun_score_list[gun_type]:
            normalized_score = (
                new_max - (new_max - new_min) * (gun[1] - min_score) / (max_score - min_score)
            ) / 100
            combined_list.append({
                "name": gun[0],
                "score": round(normalized_score, 6),
                "id": gun[2],
                "type": gun_type
            })

    # Output the combined list
    # print(json.dumps(combined_list, indent=4))
    print(combined_list)

def main():
    # Define file paths (update with your actual paths)
    file_path = "/Users/bransonboggia/Documents/Personal Documents/minecraft_stuff/custom_mods/TaCZWeaponBlueprints/src/main/resources/data/taczweaponblueprints/gun_rebalancing_data/balanced_weapon_pool_bullet_rpm_properties.json"
    gun_range_mod_path = "/Users/bransonboggia/Documents/Personal Documents/minecraft_stuff/custom_mods/TaCZWeaponBlueprints/misc_files/gunPropertyBalancing/gunDamageDropoffDistanceAndDamagePercentage.json"
    gun_min_max_ranges_path = "/Users/bransonboggia/Documents/Personal Documents/minecraft_stuff/custom_mods/TaCZWeaponBlueprints/misc_files/gunPropertyBalancing/gunMinMaxDamageRange.json"

    # Step 1: Load data from JSON files
    gun_data = load_json_data(file_path)
    new_gun_ranges = load_json_data(gun_range_mod_path)
    min_max_gun_ranges = load_json_data(gun_min_max_ranges_path)

    # Step 2: Rescale damage drop-off distances
    new_gun_ranges = rescale_damage_dropoff(new_gun_ranges, min_max_gun_ranges)

    # Step 3: Update gun data with new damage adjustments
    gun_data = update_gun_data(gun_data, new_gun_ranges)

    # Step 4: Compile gun scores
    gun_score_list = compile_gun_scores(gun_data)

    # Step 5: Normalize and output scores
    normalize_and_output_scores(gun_score_list)

if __name__ == "__main__":
    main()