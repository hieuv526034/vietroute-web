-- Khóa ngoại liên cụm; chạy sau toàn bộ PK/UNIQUE và actor system.

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'auth_access_changes'::regclass AND conname = 'auth_access_changes_actor_id_fkey') THEN
        ALTER TABLE auth_access_changes ADD CONSTRAINT auth_access_changes_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'auth_access_changes'::regclass AND conname = 'auth_access_changes_created_by_fkey') THEN
        ALTER TABLE auth_access_changes ADD CONSTRAINT auth_access_changes_created_by_fkey FOREIGN KEY (created_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'auth_access_changes'::regclass AND conname = 'auth_access_changes_updated_by_fkey') THEN
        ALTER TABLE auth_access_changes ADD CONSTRAINT auth_access_changes_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'auth_account_challenges'::regclass AND conname = 'auth_account_challenges_created_by_fkey') THEN
        ALTER TABLE auth_account_challenges ADD CONSTRAINT auth_account_challenges_created_by_fkey FOREIGN KEY (created_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'auth_account_challenges'::regclass AND conname = 'auth_account_challenges_updated_by_fkey') THEN
        ALTER TABLE auth_account_challenges ADD CONSTRAINT auth_account_challenges_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'auth_account_challenges'::regclass AND conname = 'auth_account_challenges_user_id_fkey') THEN
        ALTER TABLE auth_account_challenges ADD CONSTRAINT auth_account_challenges_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'auth_function_resources'::regclass AND conname = 'auth_function_resources_created_by_fkey') THEN
        ALTER TABLE auth_function_resources ADD CONSTRAINT auth_function_resources_created_by_fkey FOREIGN KEY (created_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'auth_function_resources'::regclass AND conname = 'auth_function_resources_function_code_fkey') THEN
        ALTER TABLE auth_function_resources ADD CONSTRAINT auth_function_resources_function_code_fkey FOREIGN KEY (function_code) REFERENCES auth_functions(code) ON DELETE CASCADE;
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'auth_function_resources'::regclass AND conname = 'auth_function_resources_updated_by_fkey') THEN
        ALTER TABLE auth_function_resources ADD CONSTRAINT auth_function_resources_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'auth_functions'::regclass AND conname = 'auth_functions_created_by_fkey') THEN
        ALTER TABLE auth_functions ADD CONSTRAINT auth_functions_created_by_fkey FOREIGN KEY (created_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'auth_functions'::regclass AND conname = 'auth_functions_parent_code_fkey') THEN
        ALTER TABLE auth_functions ADD CONSTRAINT auth_functions_parent_code_fkey FOREIGN KEY (parent_code) REFERENCES auth_functions(code);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'auth_functions'::regclass AND conname = 'auth_functions_updated_by_fkey') THEN
        ALTER TABLE auth_functions ADD CONSTRAINT auth_functions_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'auth_role_functions'::regclass AND conname = 'auth_role_functions_created_by_fkey') THEN
        ALTER TABLE auth_role_functions ADD CONSTRAINT auth_role_functions_created_by_fkey FOREIGN KEY (created_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'auth_role_functions'::regclass AND conname = 'auth_role_functions_function_code_fkey') THEN
        ALTER TABLE auth_role_functions ADD CONSTRAINT auth_role_functions_function_code_fkey FOREIGN KEY (function_code) REFERENCES auth_functions(code);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'auth_role_functions'::regclass AND conname = 'auth_role_functions_role_code_fkey') THEN
        ALTER TABLE auth_role_functions ADD CONSTRAINT auth_role_functions_role_code_fkey FOREIGN KEY (role_code) REFERENCES auth_roles(code);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'auth_role_functions'::regclass AND conname = 'auth_role_functions_updated_by_fkey') THEN
        ALTER TABLE auth_role_functions ADD CONSTRAINT auth_role_functions_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'auth_roles'::regclass AND conname = 'auth_roles_created_by_fkey') THEN
        ALTER TABLE auth_roles ADD CONSTRAINT auth_roles_created_by_fkey FOREIGN KEY (created_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'auth_roles'::regclass AND conname = 'auth_roles_updated_by_fkey') THEN
        ALTER TABLE auth_roles ADD CONSTRAINT auth_roles_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'auth_user_roles'::regclass AND conname = 'auth_user_roles_carrier_scope_id_fkey') THEN
        ALTER TABLE auth_user_roles ADD CONSTRAINT auth_user_roles_carrier_scope_id_fkey FOREIGN KEY (carrier_scope_id) REFERENCES carriers(scope_id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'auth_user_roles'::regclass AND conname = 'auth_user_roles_created_by_fkey1') THEN
        ALTER TABLE auth_user_roles ADD CONSTRAINT auth_user_roles_created_by_fkey1 FOREIGN KEY (created_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'auth_user_roles'::regclass AND conname = 'auth_user_roles_role_code_fkey1') THEN
        ALTER TABLE auth_user_roles ADD CONSTRAINT auth_user_roles_role_code_fkey1 FOREIGN KEY (role_code) REFERENCES auth_roles(code);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'auth_user_roles'::regclass AND conname = 'auth_user_roles_updated_by_fkey1') THEN
        ALTER TABLE auth_user_roles ADD CONSTRAINT auth_user_roles_updated_by_fkey1 FOREIGN KEY (updated_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'auth_user_roles'::regclass AND conname = 'auth_user_roles_user_id_fkey1') THEN
        ALTER TABLE auth_user_roles ADD CONSTRAINT auth_user_roles_user_id_fkey1 FOREIGN KEY (user_id) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'auth_user_roles'::regclass AND conname = 'auth_user_roles_user_id_role_code_global_scope_id_fkey') THEN
        ALTER TABLE auth_user_roles ADD CONSTRAINT auth_user_roles_user_id_role_code_global_scope_id_fkey FOREIGN KEY (user_id, role_code, global_scope_id) REFERENCES auth_user_roles(user_id, role_code, scope_id) ON DELETE CASCADE;
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'auth_users'::regclass AND conname = 'auth_users_created_by_fkey') THEN
        ALTER TABLE auth_users ADD CONSTRAINT auth_users_created_by_fkey FOREIGN KEY (created_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'auth_users'::regclass AND conname = 'auth_users_updated_by_fkey') THEN
        ALTER TABLE auth_users ADD CONSTRAINT auth_users_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'catalog_items'::regclass AND conname = 'catalog_items_created_by_fkey') THEN
        ALTER TABLE catalog_items ADD CONSTRAINT catalog_items_created_by_fkey FOREIGN KEY (created_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'catalog_items'::regclass AND conname = 'catalog_items_updated_by_fkey') THEN
        ALTER TABLE catalog_items ADD CONSTRAINT catalog_items_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'location_places'::regclass AND conname = 'location_places_created_by_fkey') THEN
        ALTER TABLE location_places ADD CONSTRAINT location_places_created_by_fkey FOREIGN KEY (created_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'location_places'::regclass AND conname = 'location_places_province_id_fkey') THEN
        ALTER TABLE location_places ADD CONSTRAINT location_places_province_id_fkey FOREIGN KEY (province_id) REFERENCES location_provinces(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'location_places'::regclass AND conname = 'location_places_updated_by_fkey') THEN
        ALTER TABLE location_places ADD CONSTRAINT location_places_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'location_provinces'::regclass AND conname = 'location_provinces_created_by_fkey') THEN
        ALTER TABLE location_provinces ADD CONSTRAINT location_provinces_created_by_fkey FOREIGN KEY (created_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'location_provinces'::regclass AND conname = 'location_provinces_updated_by_fkey') THEN
        ALTER TABLE location_provinces ADD CONSTRAINT location_provinces_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'carrier_drivers'::regclass AND conname = 'carrier_drivers_carrier_id_scope_id_fkey') THEN
        ALTER TABLE carrier_drivers ADD CONSTRAINT carrier_drivers_carrier_id_scope_id_fkey FOREIGN KEY (carrier_id, scope_id) REFERENCES carriers(id, scope_id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'carrier_drivers'::regclass AND conname = 'carrier_drivers_created_by_fkey') THEN
        ALTER TABLE carrier_drivers ADD CONSTRAINT carrier_drivers_created_by_fkey FOREIGN KEY (created_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'carrier_drivers'::regclass AND conname = 'carrier_drivers_updated_by_fkey') THEN
        ALTER TABLE carrier_drivers ADD CONSTRAINT carrier_drivers_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'carrier_drivers'::regclass AND conname = 'carrier_drivers_user_id_fkey') THEN
        ALTER TABLE carrier_drivers ADD CONSTRAINT carrier_drivers_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'carrier_vehicles'::regclass AND conname = 'carrier_vehicles_carrier_id_scope_id_fkey') THEN
        ALTER TABLE carrier_vehicles ADD CONSTRAINT carrier_vehicles_carrier_id_scope_id_fkey FOREIGN KEY (carrier_id, scope_id) REFERENCES carriers(id, scope_id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'carrier_vehicles'::regclass AND conname = 'carrier_vehicles_created_by_fkey') THEN
        ALTER TABLE carrier_vehicles ADD CONSTRAINT carrier_vehicles_created_by_fkey FOREIGN KEY (created_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'carrier_vehicles'::regclass AND conname = 'carrier_vehicles_updated_by_fkey') THEN
        ALTER TABLE carrier_vehicles ADD CONSTRAINT carrier_vehicles_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'carrier_vehicles'::regclass AND conname = 'carrier_vehicles_vehicle_type_id_vehicle_type_fkey') THEN
        ALTER TABLE carrier_vehicles ADD CONSTRAINT carrier_vehicles_vehicle_type_id_vehicle_type_fkey FOREIGN KEY (vehicle_type_id, vehicle_type) REFERENCES catalog_items(id, type);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'carriers'::regclass AND conname = 'carriers_created_by_fkey') THEN
        ALTER TABLE carriers ADD CONSTRAINT carriers_created_by_fkey FOREIGN KEY (created_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'carriers'::regclass AND conname = 'carriers_updated_by_fkey') THEN
        ALTER TABLE carriers ADD CONSTRAINT carriers_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'carrier_route_stops'::regclass AND conname = 'carrier_route_stops_created_by_fkey') THEN
        ALTER TABLE carrier_route_stops ADD CONSTRAINT carrier_route_stops_created_by_fkey FOREIGN KEY (created_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'carrier_route_stops'::regclass AND conname = 'carrier_route_stops_location_id_fkey') THEN
        ALTER TABLE carrier_route_stops ADD CONSTRAINT carrier_route_stops_location_id_fkey FOREIGN KEY (location_id) REFERENCES location_places(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'carrier_route_stops'::regclass AND conname = 'carrier_route_stops_route_id_carrier_id_scope_id_fkey') THEN
        ALTER TABLE carrier_route_stops ADD CONSTRAINT carrier_route_stops_route_id_carrier_id_scope_id_fkey FOREIGN KEY (route_id, carrier_id, scope_id) REFERENCES carrier_routes(id, carrier_id, scope_id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'carrier_route_stops'::regclass AND conname = 'carrier_route_stops_updated_by_fkey') THEN
        ALTER TABLE carrier_route_stops ADD CONSTRAINT carrier_route_stops_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'carrier_routes'::regclass AND conname = 'carrier_routes_carrier_id_scope_id_fkey') THEN
        ALTER TABLE carrier_routes ADD CONSTRAINT carrier_routes_carrier_id_scope_id_fkey FOREIGN KEY (carrier_id, scope_id) REFERENCES carriers(id, scope_id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'carrier_routes'::regclass AND conname = 'carrier_routes_created_by_fkey') THEN
        ALTER TABLE carrier_routes ADD CONSTRAINT carrier_routes_created_by_fkey FOREIGN KEY (created_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'carrier_routes'::regclass AND conname = 'carrier_routes_updated_by_fkey') THEN
        ALTER TABLE carrier_routes ADD CONSTRAINT carrier_routes_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'carrier_trips'::regclass AND conname = 'carrier_trips_carrier_id_scope_id_fkey') THEN
        ALTER TABLE carrier_trips ADD CONSTRAINT carrier_trips_carrier_id_scope_id_fkey FOREIGN KEY (carrier_id, scope_id) REFERENCES carriers(id, scope_id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'carrier_trips'::regclass AND conname = 'carrier_trips_created_by_fkey') THEN
        ALTER TABLE carrier_trips ADD CONSTRAINT carrier_trips_created_by_fkey FOREIGN KEY (created_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'carrier_trips'::regclass AND conname = 'carrier_trips_driver_id_carrier_id_scope_id_fkey') THEN
        ALTER TABLE carrier_trips ADD CONSTRAINT carrier_trips_driver_id_carrier_id_scope_id_fkey FOREIGN KEY (driver_id, carrier_id, scope_id) REFERENCES carrier_drivers(id, carrier_id, scope_id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'carrier_trips'::regclass AND conname = 'carrier_trips_driver_user_id_fkey') THEN
        ALTER TABLE carrier_trips ADD CONSTRAINT carrier_trips_driver_user_id_fkey FOREIGN KEY (driver_user_id) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'carrier_trips'::regclass AND conname = 'carrier_trips_route_id_carrier_id_scope_id_fkey') THEN
        ALTER TABLE carrier_trips ADD CONSTRAINT carrier_trips_route_id_carrier_id_scope_id_fkey FOREIGN KEY (route_id, carrier_id, scope_id) REFERENCES carrier_routes(id, carrier_id, scope_id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'carrier_trips'::regclass AND conname = 'carrier_trips_updated_by_fkey') THEN
        ALTER TABLE carrier_trips ADD CONSTRAINT carrier_trips_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'carrier_trips'::regclass AND conname = 'carrier_trips_vehicle_id_carrier_id_scope_id_fkey') THEN
        ALTER TABLE carrier_trips ADD CONSTRAINT carrier_trips_vehicle_id_carrier_id_scope_id_fkey FOREIGN KEY (vehicle_id, carrier_id, scope_id) REFERENCES carrier_vehicles(id, carrier_id, scope_id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'carrier_trips'::regclass AND conname = 'carrier_trips_vehicle_type_id_fkey') THEN
        ALTER TABLE carrier_trips ADD CONSTRAINT carrier_trips_vehicle_type_id_fkey FOREIGN KEY (vehicle_type_id) REFERENCES catalog_items(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'trip_events'::regclass AND conname = 'trip_events_created_by_fkey') THEN
        ALTER TABLE trip_events ADD CONSTRAINT trip_events_created_by_fkey FOREIGN KEY (created_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'trip_events'::regclass AND conname = 'trip_events_trip_id_carrier_id_scope_id_fkey') THEN
        ALTER TABLE trip_events ADD CONSTRAINT trip_events_trip_id_carrier_id_scope_id_fkey FOREIGN KEY (trip_id, carrier_id, scope_id) REFERENCES carrier_trips(id, carrier_id, scope_id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'trip_events'::regclass AND conname = 'trip_events_trip_id_stop_order_fkey') THEN
        ALTER TABLE trip_events ADD CONSTRAINT trip_events_trip_id_stop_order_fkey FOREIGN KEY (trip_id, stop_order) REFERENCES trip_stops(trip_id, stop_order);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'trip_segments'::regclass AND conname = 'trip_segments_trip_id_carrier_id_scope_id_fkey') THEN
        ALTER TABLE trip_segments ADD CONSTRAINT trip_segments_trip_id_carrier_id_scope_id_fkey FOREIGN KEY (trip_id, carrier_id, scope_id) REFERENCES carrier_trips(id, carrier_id, scope_id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'trip_segments'::regclass AND conname = 'trip_segments_updated_by_fkey') THEN
        ALTER TABLE trip_segments ADD CONSTRAINT trip_segments_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'trip_stops'::regclass AND conname = 'trip_stops_created_by_fkey') THEN
        ALTER TABLE trip_stops ADD CONSTRAINT trip_stops_created_by_fkey FOREIGN KEY (created_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'trip_stops'::regclass AND conname = 'trip_stops_location_id_fkey') THEN
        ALTER TABLE trip_stops ADD CONSTRAINT trip_stops_location_id_fkey FOREIGN KEY (location_id) REFERENCES location_places(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'trip_stops'::regclass AND conname = 'trip_stops_province_id_fkey') THEN
        ALTER TABLE trip_stops ADD CONSTRAINT trip_stops_province_id_fkey FOREIGN KEY (province_id) REFERENCES location_provinces(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'trip_stops'::regclass AND conname = 'trip_stops_trip_id_carrier_id_scope_id_fkey') THEN
        ALTER TABLE trip_stops ADD CONSTRAINT trip_stops_trip_id_carrier_id_scope_id_fkey FOREIGN KEY (trip_id, carrier_id, scope_id) REFERENCES carrier_trips(id, carrier_id, scope_id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'trip_stops'::regclass AND conname = 'trip_stops_updated_by_fkey') THEN
        ALTER TABLE trip_stops ADD CONSTRAINT trip_stops_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'booking_events'::regclass AND conname = 'booking_events_booking_id_trip_id_carrier_id_scope_id_fkey') THEN
        ALTER TABLE booking_events ADD CONSTRAINT booking_events_booking_id_trip_id_carrier_id_scope_id_fkey FOREIGN KEY (booking_id, trip_id, carrier_id, scope_id) REFERENCES bookings(id, trip_id, carrier_id, scope_id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'booking_events'::regclass AND conname = 'booking_events_created_by_fkey') THEN
        ALTER TABLE booking_events ADD CONSTRAINT booking_events_created_by_fkey FOREIGN KEY (created_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'bookings'::regclass AND conname = 'bookings_created_by_fkey') THEN
        ALTER TABLE bookings ADD CONSTRAINT bookings_created_by_fkey FOREIGN KEY (created_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'bookings'::regclass AND conname = 'bookings_customer_id_fkey') THEN
        ALTER TABLE bookings ADD CONSTRAINT bookings_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'bookings'::regclass AND conname = 'bookings_dropoff_stop_id_trip_id_carrier_id_scope_id_fkey') THEN
        ALTER TABLE bookings ADD CONSTRAINT bookings_dropoff_stop_id_trip_id_carrier_id_scope_id_fkey FOREIGN KEY (dropoff_stop_id, trip_id, carrier_id, scope_id) REFERENCES trip_stops(id, trip_id, carrier_id, scope_id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'bookings'::regclass AND conname = 'bookings_pickup_stop_id_trip_id_carrier_id_scope_id_fkey') THEN
        ALTER TABLE bookings ADD CONSTRAINT bookings_pickup_stop_id_trip_id_carrier_id_scope_id_fkey FOREIGN KEY (pickup_stop_id, trip_id, carrier_id, scope_id) REFERENCES trip_stops(id, trip_id, carrier_id, scope_id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'bookings'::regclass AND conname = 'bookings_source_id_source_type_fkey') THEN
        ALTER TABLE bookings ADD CONSTRAINT bookings_source_id_source_type_fkey FOREIGN KEY (source_id, source_type) REFERENCES catalog_items(id, type);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'bookings'::regclass AND conname = 'bookings_trip_id_carrier_id_scope_id_fkey') THEN
        ALTER TABLE bookings ADD CONSTRAINT bookings_trip_id_carrier_id_scope_id_fkey FOREIGN KEY (trip_id, carrier_id, scope_id) REFERENCES carrier_trips(id, carrier_id, scope_id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'bookings'::regclass AND conname = 'bookings_updated_by_fkey') THEN
        ALTER TABLE bookings ADD CONSTRAINT bookings_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'api_request_journal'::regclass AND conname = 'api_request_journal_created_by_fkey') THEN
        ALTER TABLE api_request_journal ADD CONSTRAINT api_request_journal_created_by_fkey FOREIGN KEY (created_by) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'api_request_journal'::regclass AND conname = 'api_request_journal_request_user_id_fkey') THEN
        ALTER TABLE api_request_journal ADD CONSTRAINT api_request_journal_request_user_id_fkey FOREIGN KEY (request_user_id) REFERENCES auth_users(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'api_request_journal'::regclass AND conname = 'api_request_journal_updated_by_fkey') THEN
        ALTER TABLE api_request_journal ADD CONSTRAINT api_request_journal_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES auth_users(id);
    END IF;
END $$;
