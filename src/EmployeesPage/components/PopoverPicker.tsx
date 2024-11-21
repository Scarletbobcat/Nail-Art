import { useCallback, useRef, useState } from "react";
import { HexColorPicker } from "react-colorful";

import useClickOutside from "./useClickOutside";

export const PopoverPicker = ({
  color,
  onChange,
}: {
  color: string;
  onChange: (e: string) => void;
}) => {
  const popover = useRef<HTMLDivElement>(null);
  const [isOpen, toggle] = useState(false);

  const close = useCallback(() => toggle(false), []);
  useClickOutside(popover, close);

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

      {isOpen && (
        <div
          className="popover"
          ref={popover}
          style={{ position: "absolute", zIndex: 99 }}
        >
          <HexColorPicker color={color} onChange={onChange} />
        </div>
      )}
    </div>
  );
};
