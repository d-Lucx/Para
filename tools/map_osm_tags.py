"""Shared display attributes for Para's OSM extractors."""

STREET_AMENITIES = {
    'restaurant': 'Restaurant', 'cafe': 'Cafe', 'fast_food': 'Fast food',
    'bank': 'Bank', 'atm': 'ATM', 'hospital': 'Hospital', 'clinic': 'Clinic',
    'pharmacy': 'Pharmacy', 'fuel': 'Fuel',
}


def road_properties(tags):
    direction = tags.get('oneway')
    if direction in ('yes', '1', 'true') or (
        direction is None and (tags.get('highway') == 'motorway' or tags.get('junction') == 'roundabout')
    ):
        oneway = 1
    elif direction == '-1':
        oneway = -1
    else:
        oneway = 0
    return dict(
        name=(tags.get('name') or tags.get('name:en') or tags.get('ref') or '')[:140],
        oneway=oneway,
    )


def street_amenity(tags):
    if tags.get('highway') == 'traffic_signals':
        return 'Traffic signal'
    return STREET_AMENITIES.get(tags.get('amenity'))
