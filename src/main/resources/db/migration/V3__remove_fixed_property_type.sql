SET search_path TO app, extensions, public;

-- Classification is now sourced from versioned dynamic property attributes.
-- Keeping a fixed property_type dimension would recreate the removed domain enum.
ALTER TABLE dim_property DROP COLUMN property_type;
