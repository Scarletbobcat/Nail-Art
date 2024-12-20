import { useState } from "react";
import { HexColorPicker } from "react-colorful";
import { useClickAway } from "@uidotdev/usehooks";

// import useClickOutside from "./useClickOutside";

export const PopoverPicker = ({
  color,
  onChange,
  disabled,
}: {
  color: string;
  onChange: (e: string) => void;
  disabled?: boolean;
}) => {
  const [isOpen, toggle] = useState(false);

  // const close = useCallback(() => toggle(false), []);
  // useClickOutside(popover, close);

  const ref = useClickAway<HTMLDivElement>(() => {
    toggle(false);
  });

  return (
    <div className="picker">
      <div
        className="swatch"
        style={{
          backgroundColor: color,
          width: 65,
          height: 57,
          borderRadius: 4,
        }}
        onClick={() => toggle(true)}
      />

      {isOpen && !disabled && (
        <div
          className="popover"
          ref={ref}
          style={{ position: "absolute", zIndex: 99 }}
        >
          <HexColorPicker color={color} onChange={onChange} />
        </div>
      )}
    </div>
  );
};
