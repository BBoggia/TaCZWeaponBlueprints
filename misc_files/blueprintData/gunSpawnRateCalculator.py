import os
import json


filePath = "/Users/bransonboggia/Documents/Personal Documents/minecraft_stuff/custom_mods/TaCZWeaponBlueprints/src/main/resources/data/taczweaponblueprints/gun_rebalancing_data/balanced_weapon_pool_bullet_rpm_properties.json"
gunRangeModPath = "/Users/bransonboggia/Documents/Personal Documents/minecraft_stuff/custom_mods/TaCZWeaponBlueprints/misc_files/gunPropertyBalancing/gunDamageDropoffDistanceAndDamagePercentage.json"
gunMinMaxRangesPath = "/Users/bransonboggia/Documents/Personal Documents/minecraft_stuff/custom_mods/TaCZWeaponBlueprints/misc_files/gunPropertyBalancing/gunMinMaxDamageRange.json"

# def calculate_gun_score(gun):
#     # Constants and weights
#     MAX_ALPHA_DAMAGE = 200  
#     MAX_EXPLOSION_RADIUS = 5
#     MAX_EFFECTIVE_RANGE = 300  
#     MAX_INACCURACY = 8
#     MAX_RECOIL = 1;.75  
#     MAX_EFFECTIVE_DPS = 200  # For standard weapons

#     WEIGHTS = {
#         'alpha_damage': 6,
#         'explosion_radius': 3,
#         'effective_range': 3,
#         'inaccuracy': 3,
#         'recoil': 2,
#         'firing_mode': 2,
#         'armor_ignore': 2,
#         'effective_dps': 2,
#         'knockback': 1,
#         'ignite': 1
#     }

#     FIRING_MODE_SCORES = {
#         'auto': 1.0,
#         'burst': 0.8,
#         'semi': 0.5
#     }

#     INACCURACY_WEIGHTS = {
#         'stand': 0.4,
#         'move': 0.3,
#         'sneak': 0.1,
#         'lie': 0.05,
#         'aim': 0.15
#     }

#     # Helper function to get damage at a specific distance
#     def get_firing_mode_score(fire_modes):
#         max_score = 0
#         for mode in fire_modes:
#             mode_score = FIRING_MODE_SCORES.get(mode.lower(), 0)
#             max_score = max(max_score, mode_score)
#         return max_score
    
#     def calculate_average_recoil(recoil_data):
#         total = 0
#         count = 0
#         for keyframe in recoil_data:
#             value_range = keyframe.get('value', [0, 0])
#             mean_value = sum(value_range) / len(value_range)
#             total += abs(mean_value)
#             count += 1
#         return total / count if count > 0 else 0
    
#     def calculate_average_inaccuracy(inaccuracy_data):
#         total = 0
#         for state, weight in INACCURACY_WEIGHTS.items():
#             state_inaccuracy = inaccuracy_data.get(state, 0)
#             total += state_inaccuracy * weight
#         return total
    
#     # Calculate properties
#     sps = gun.get('rpm', 0) / 60  # Shots per second
#     # Calculate properties
#     # Alpha Damage
#     bullet_info = gun.get('bullet', {})
#     bullet_damage = bullet_info.get('damage', 0)
#     explosion_data = bullet_info.get('explosion', {})
#     explosion_damage = explosion_data.get('damage', 0)
#     alpha_damage = bullet_damage + explosion_damage
#     alpha_damage_score = min(alpha_damage / MAX_ALPHA_DAMAGE, 1)

#     # Explosion Radius
#     explosion_radius = explosion_data.get('radius', 0)
#     explosion_radius_score = min(explosion_radius / MAX_EXPLOSION_RADIUS, 1)

#     # Effective Range
#     bullet_speed = bullet_info.get('speed', 0)
#     bullet_life = bullet_info.get('life', 0)
#     gun_range = bullet_speed * bullet_life
#     effective_range_score = min(gun_range / MAX_EFFECTIVE_RANGE, 1)

#     # Inaccuracy
#     inaccuracy_data = gun.get('inaccuracy', {})
#     avg_inaccuracy = calculate_average_inaccuracy(inaccuracy_data)
#     inaccuracy_score = 1 - (avg_inaccuracy / MAX_INACCURACY)
#     inaccuracy_score = max(min(inaccuracy_score, 1), 0)

#     # Recoil
#     recoil_data = gun.get('recoil', {})
#     pitch_recoil_data = recoil_data.get('pitch', [])
#     yaw_recoil_data = recoil_data.get('yaw', [])
#     avg_pitch_recoil = calculate_average_recoil(pitch_recoil_data)
#     avg_yaw_recoil = calculate_average_recoil(yaw_recoil_data)
#     total_recoil = (avg_pitch_recoil ** 2 + avg_yaw_recoil ** 2) ** 0.5
#     recoil_score = 1 - (total_recoil / MAX_RECOIL)
#     recoil_score = max(min(recoil_score, 1), 0)

#     # Firing Mode
#     fire_modes = gun.get('fire_mode', [])
#     firing_mode_score = get_firing_mode_score(fire_modes)

#     # Armor Ignore
#     armor_ignore = bullet_info.get('extra_damage', {}).get('armor_ignore', 0.0)
#     armor_ignore_score = min(armor_ignore / 1.0, 1)

#     # Effective DPS (Optional lower weight)
#     sps = gun.get('rpm', 0) / 60  # Shots per second
#     reload_time = gun.get('reload', {}).get('cooldown', {}).get('empty', 0)
#     time_per_shot = (1 / sps) + reload_time if sps > 0 else reload_time
#     sustained_dps = alpha_damage / time_per_shot if time_per_shot > 0 else 0
#     effective_dps_score = min(sustained_dps / MAX_EFFECTIVE_DPS, 1)

#     # Knockback
#     knockback = bullet_info.get('knockback', 0)
#     knockback_score = min(knockback / 1.0, 1)

#     # Ignite
#     ignite_score = 1 if bullet_info.get('ignite', False) else 0

#     # Total Weighted Score
#     total_score = (
#         alpha_damage_score * WEIGHTS['alpha_damage'] +
#         explosion_radius_score * WEIGHTS['explosion_radius'] +
#         effective_range_score * WEIGHTS['effective_range'] +
#         inaccuracy_score * WEIGHTS['inaccuracy'] +
#         recoil_score * WEIGHTS['recoil'] +
#         firing_mode_score * WEIGHTS['firing_mode'] +
#         armor_ignore_score * WEIGHTS['armor_ignore'] +
#         effective_dps_score * WEIGHTS['effective_dps'] +
#         knockback_score * WEIGHTS['knockback'] +
#         ignite_score * WEIGHTS['ignite']
#     )
    
#     max_total_score = sum(WEIGHTS.values())

#     normalized_score = (total_score / max_total_score) * 100  # Percentage score

#     return normalized_score

def calculate_gun_score(gun):
    # Constants and weights
    MAX_DPS = 75
    MAX_ALPHA_DAMAGE = 120
    MAX_EXPLOSION_RADIUS = 5
    MAX_RANGE = 125 
    MAX_PIERCE = 4
    MAX_ARMOR_IGNORE = 1.0
    MAX_HEADSHOT_MULTIPLIER = 3.5
    MAX_KNOCKBACK = 1.0
    AVERAGE_ARMOR_REDUCTION = 0.25  # Average enemy armor reduction
    MAX_RECOIL = 1.0
    MAX_INACCURACY = 6.0  
    # MAX_MAGAZINE_SIZE = 100

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
        # 'magazine_size': 1.5
    }
    
    # WEIGHTS = {
    #     'alpha_damage': 6,
    #     'explosion_radius': 4,
    #     'dps': 5,
    #     'range': 3,
    #     'recoil': 2.5,
    #     'inaccuracy': 2.5,
    #     'firing_mode': 2.25,
    #     'pierce': 2,
    #     'armor_ignore': 3,
    #     'headshot': 1.5,
    #     'knockback': 1,
    #     'ignite': 1
    # }

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

    # Helper function to get damage at a specific distance
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
                    # Linear interpolation between previous and current entry
                    prev_dist = previous_entry['distance']
                    if prev_dist == "infinite":
                        prev_dist = float('inf')
                    prev_damage = previous_entry['damage']

                    # Prevent division by zero
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
        sps = gun.get('rpm', 0)  / 60  # Shots per second
    else:
        burst_data = bullet_info.get('burst_data', {})
        burst_count = burst_data.get('count', 1)
        burst_delay = burst_data.get('min_interval', 0)
        burst_bpm = burst_data.get('bpm', 0)
        sps = burst_count / burst_delay * burst_bpm / 60
        rpm = gun.get('rpm', 0) / 60
        if sps < rpm:
            sps = rpm
        
    if 'extra_damage' in bullet_info and 'damage_adjust' in bullet_info['extra_damage']:
        damage_adjust_list = bullet_info.get('extra_damage', {}).get('damage_adjust', [])
        damage = get_damage_at_distance(damage_adjust_list, 30)  # Damage at 30 units
    else:
        damage = None
        
    
    if 'explosion' in bullet_info:
        explosion_data = bullet_info.get('explosion', {})
        explosion_damage = explosion_data.get('damage', 0)
        alpha_damage = explosion_damage
        alpha_damage_score = min(alpha_damage / MAX_ALPHA_DAMAGE, 1)
        damage = alpha_damage + bullet_info.get('damage', 0)

        explosion_radius = explosion_data.get('radius', 0)
        explosion_radius_score = min(explosion_radius / MAX_EXPLOSION_RADIUS, 1)
    else:
        alpha_damage_score = 0
        explosion_radius_score = 0
    
    headshot_rate = 0.25  # Assumed average headshot rate
    headshot_multiplier = bullet_info.get('extra_damage', {}).get('head_shot_multiplier', 1.0)
    armor_ignore = bullet_info.get('extra_damage', {}).get('armor_ignore', 0.0)

    if damage is None and 'damage' in bullet_info:
        damage = bullet_info.get('damage', 0)

    # Calculate effective damage per shot
    base_damage = ((1 - headshot_rate) * damage + headshot_rate * damage * headshot_multiplier)
    adjusted_armor_reduction = AVERAGE_ARMOR_REDUCTION * (1 - armor_ignore)
    effective_damage = base_damage * (1 - adjusted_armor_reduction)
    bullet_amount = bullet_info.get('bullet_amount', 1)

    effective_dps = effective_damage * sps * bullet_amount
    dps_score = min(effective_dps / MAX_DPS, 1)
    
    # Calculate effective range
    bullet_speed = bullet_info.get('speed', 0)
    bullet_life = bullet_info.get('life', 0)
    gun_range = bullet_speed * bullet_life  # Maximum range of the gun
    range_score = min(gun_range / MAX_RANGE, 1)

    # Normalize other properties
    pierce = bullet_info.get('pierce', 0)
    pierce_score = min(pierce / MAX_PIERCE, 1)

    armor_ignore_score = min(armor_ignore / MAX_ARMOR_IGNORE, 1)

    if MAX_HEADSHOT_MULTIPLIER - 1 == 0:
        headshot_score = 0
    else:
        headshot_score = (headshot_multiplier - 1) / (MAX_HEADSHOT_MULTIPLIER - 1)
    headshot_score = min(max(headshot_score, 0), 1)  # Ensure score is between 0 and 1

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

    # Normalize Recoil Score
    recoil_score = 1 - (total_recoil / MAX_RECOIL)
    recoil_score = max(min(recoil_score, 1), 0)

    # Inaccuracy calculation
    inaccuracy_data = gun.get('inaccuracy', {})
    avg_inaccuracy = calculate_average_inaccuracy(inaccuracy_data)

    # Normalize Inaccuracy Score
    inaccuracy_score = 1 - (avg_inaccuracy / MAX_INACCURACY)
    inaccuracy_score = max(min(inaccuracy_score, 1), 0)

    # Firing mode calculation
    fire_modes = gun.get('fire_mode', [])
    firing_mode_score = get_firing_mode_score(fire_modes)
    
    # Magazine size calculation
    # magazine_size = gun.get('ammo_amount', 1)
    # magazine_score = min(magazine_size / MAX_MAGAZINE_SIZE, 1)

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
        # magazine_score * WEIGHTS['magazine_size']
    )
    max_total_score = sum(WEIGHTS.values())

    normalized_score = (total_score / max_total_score) * 100  # Percentage score

    return normalized_score


gunData = {}
newGunRanges = {}
minMaxGunRanges = {}

try:
    with open(filePath) as f:
        gunData = json.load(f)
except FileNotFoundError:
    print(f"File not found: {filePath}")
    gunData = {}
except json.JSONDecodeError as e:
    print(f"JSON decode error in {filePath}: {e}")
    gunData = {}
    
with open(gunRangeModPath) as f:
    newGunRanges = json.load(f)
    
with open(gunMinMaxRangesPath) as f:
    minMaxGunRanges = json.load(f)
    
    
#
#
# THIS RESCALED THE VALUES THAT WERE IN THE gunDamageDropoffDistanceAndDamagePercentage.json FILE
#
for i in newGunRanges:
    for j in range(len(newGunRanges[i])):
        thisMaxDistance = max([k["distance"] for k in newGunRanges[i][j]["damage_drop"] if k["distance"] != "infinite"])
        thisMinDistance = min([k["distance"] for k in newGunRanges[i][j]["damage_drop"] if k["distance"] != "infinite"])
        thisMaxDamage = max([k["damage"] for k in newGunRanges[i][j]["damage_drop"] if k["distance"] != "infinite"])
        thisMinDamage = min([k["damage"] for k in newGunRanges[i][j]["damage_drop"] if k["distance"] != "infinite"])
        
        newRangeDamageList = []
        
        # Find 5 values the are evenly spaced between the min and max values including the min and max values
        for k in range(5):
            damageRange = (thisMinDistance + (thisMaxDistance - thisMinDistance) / 4 * k)
            damage = (thisMinDamage + (thisMaxDamage - thisMinDamage) / 4 * (4 - k))
            newRangeDamageList.append({"distance": round(damageRange, 2), "damage": round(damage, 3)})
            
        if newGunRanges[i][j]['damage_drop'][-1]['damage'] < newRangeDamageList[-1]['damage'] * 0.9:
            newRangeDamageList.append({"distance": "infinite", "damage": round(newGunRanges[i][j]['damage_drop'][-1]['damage'], 3)})
        else:
            newRangeDamageList.append({"distance": "infinite", "damage": round(newGunRanges[i][j]['damage_drop'][-1]['damage'] * 0.75, 3)})
            
        # j["damage_drop"] = newRangeDamageList
        newGunRanges[i][j]["damage_drop"] = newRangeDamageList
        newGunRanges[i][j]['id'] = gunData[i][j]['id']
        
        
#
#
# THIS BUILT THE gunDamageDropoffDistanceAndDamagePercentage.json FILE
#
for i in newGunRanges:
    # Find the minimum distance across all damage drop entries for the gun type
    oldMinDistance = min([k["distance"] for j in newGunRanges[i] for k in j["damage_drop"] if k["distance"] != "infinite"])
    oldMaxDistance = max([k["distance"] for j in newGunRanges[i] for k in j["damage_drop"] if k["distance"] != "infinite"])
    
    for j in newGunRanges[i]:
        
        scale = (minMaxGunRanges[i]["damageFallOffEnd"] - minMaxGunRanges[i]["damageFallOffStart"]) / (oldMaxDistance - oldMinDistance)
        rescaled = [minMaxGunRanges[i]["damageFallOffStart"] + (num - oldMinDistance) * scale for num in [k["distance"] for k in j["damage_drop"] if k["distance"] != "infinite"]]
        
        for index, k in enumerate(j["damage_drop"]):
            if index == len(rescaled):
                break
            k["distance"] = (round(rescaled[index], 2))
            
    
for gunType in gunData:
    if gunType == "rpg":
        continue
    oldMax = max(gunData[gunType], key=lambda x: x["bullet"]["damage"])["bullet"]["damage"]
    oldMin = min(gunData[gunType], key=lambda x: x["bullet"]["damage"])["bullet"]["damage"]

    for index, gun in enumerate(gunData[gunType]):
        try:
            newDamageAdjust = newGunRanges[gunType][index]
                
            if newDamageAdjust['id'] == gun['id']:
                for r in newDamageAdjust['damage_drop']:
                    r['damage'] = round(r['damage'] * gunData[gunType][index]["bullet"]['damage'], 3)
                gun["bullet"]["extra_damage"]["damage_adjust"] = newDamageAdjust['damage_drop']
            else:
                newDamageAdjust = [x for x in newGunRanges[gunType] if x['id'] == gun['id']]
                if newDamageAdjust == []:
                    print("Error finding damage adjust for " + gun['name'] + "  -  " + gunType)
                    continue
                for r in newDamageAdjust['damage_drop']:
                    r['damage'] = round(r['damage'] * gunData[gunType][index]["bullet"]['damage'], 3)
                gun["bullet"]["extra_damage"]["damage_adjust"] = newDamageAdjust[0]['damage_drop']
            
        except Exception as e:
            print(e)
            print("Error calculating score for " + gun['name'] + "  -  " + gunType)

# print(gunData)

# with open(filePath, 'w') as f:
#     json.dump(gunData, f, indent=4)    

    
gunScoreList = {}
    
for gunType in gunData:
    gunScoreList[gunType] = []
    for gun in gunData[gunType]:
        try:
            gunScoreList[gunType].append((gun['name'], calculate_gun_score(gun), gun['id'], gunType))
        except Exception as e:
            print("Error calculating score for " + gun['name'] + "  -  " + str(e))


# for gunType in gunScoreList:
#     print(gunType)
#     for gun in sorted(gunScoreList[gunType], key=lambda x: x[1], reverse=True):
#         print(gun[0] + ": " + str(gun[1]))
#     print("\n")
    
# combinedList = [(gun[0], gun[1], gun[2]) for gunType in gunScoreList for gun in gunScoreList[gunType]]
combinedList = {gunType: [] for gunType in gunScoreList}

maxScore = max([gun[1] for gunType in gunScoreList for gun in gunScoreList[gunType]])
minScore = min([gun[1] for gunType in gunScoreList for gun in gunScoreList[gunType]])

newMax = 20
newMin = 0.4

combinedList = {gunType: [{"name": gun[0], "score": round((newMax - (newMax - newMin) * (gun[1] - minScore) / (maxScore - minScore)) / 100, 6), "id": gun[2]} for gun in sorted(gunScoreList[gunType], key=lambda x: x[1], reverse=True)] for gunType in gunScoreList}

# Print the combined list formatted properly for json
print(str(json.dumps(combinedList, indent=4)).replace("\n            \"name\"", "\"name\"").replace("\n            \"score\"", "\"score\"").replace("\n            \"id\"", "\"id\"").replace("\n        }", "}"))

tmp = [(gunType, gun["name"], gun["id"], gun["score"]) for gunType in combinedList for gun in combinedList[gunType]]

# Rescale the scores in tmp to between 1 and 41
maxScore = max([gun[3] for gun in tmp])
minScore = min([gun[3] for gun in tmp])

newMax = 20
newMin = 0.4

for i in range(len(tmp)):
    tmp[i] = (tmp[i][0], tmp[i][1], tmp[i][2], round(newMax - (newMax - newMin) * (tmp[i][3] - minScore) / (maxScore - minScore), 4))
    
print("\n".join(["{} -   ({}) {}: {}".format(gun[0], gun[1], gun[2], gun[3]) for gun in sorted(tmp, key=lambda x: x[3], reverse=True)]))
